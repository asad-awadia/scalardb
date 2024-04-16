package com.scalar.db.util.groupcommit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import com.google.common.util.concurrent.Uninterruptibles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GroupCommitterTest {
  private static final int OLD_GROUP_ABORT_TIMEOUT_SECONDS = 60;
  private static final int TIMEOUT_CHECK_INTERVAL_MILLIS = 10;

  @Mock private Emittable<String, Integer> emitter;

  private GroupCommitter<String, String, String, String, Integer> createGroupCommitter(
      int slotCapacity, int groupSizeFixTimeoutMillis, int delayedSlotMoveTimeoutMillis) {
    return new GroupCommitter<>(
        "test",
        new GroupCommitConfig(
            slotCapacity,
            groupSizeFixTimeoutMillis,
            delayedSlotMoveTimeoutMillis,
            OLD_GROUP_ABORT_TIMEOUT_SECONDS,
            TIMEOUT_CHECK_INTERVAL_MILLIS),
        new TestableKeyManipulator());
  }

  @Test
  void reserve_GivenArbitraryChildKey_ShouldReturnFullKeyProperly() throws Exception {
    // Arrange
    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(emitter);

      // Act
      // Assert

      assertThat(groupCommitter.reserve("child-key-1")).isEqualTo("0000:child-key-1");
      assertThat(groupCommitter.reserve("child-key-2")).isEqualTo("0000:child-key-2");
      assertThat(groupCommitter.reserve("child-key-3")).isEqualTo("0001:child-key-3");

      verify(emitter, never()).execute(any(), any());
    }
  }

  @Test
  void ready_WhenTwoSlotsAreReadyInNormalGroup_WithSuccessfulEmitTask_ShouldEmitThem()
      throws Exception {
    // Arrange
    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(emitter);
      ExecutorService executorService = Executors.newCachedThreadPool();

      // Reserve 3 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      groupCommitter.reserve("child-key-3");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])
      // - NormalGroup("0001", OPEN, slots:[Slot("child-key-3")])

      // Act

      List<Future<?>> futures = new ArrayList<>();
      // Mark the 2 slots in the first group as ready by putting values.
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey1, 11)));
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey2, 22)));
      executorService.shutdown();
      // Wait until they're done.
      for (Future<?> future : futures) {
        future.get();
      }
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot("child-key-1"), Slot("child-key-2")])
      // - NormalGroup("0001", OPEN, slots:[Slot("child-key-3")])

      // Assert
      verify(emitter).execute("0000", Arrays.asList(11, 22));
      verify(emitter, never()).execute(eq("0001"), any());
    }
  }

  @Test
  void ready_WhenTwoSlotsAreReadyInNormalGroup_WithFailingEmitTask_ShouldFail() throws Exception {
    // Arrange
    Emittable<String, Integer> failingEmitter =
        spy(
            // This should be an anonymous class since `spy()` can't handle a lambda.
            new Emittable<String, Integer>() {
              @Override
              public void execute(String key, List<Integer> values) {
                throw new RuntimeException("Something is wrong");
              }
            });

    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(failingEmitter);
      ExecutorService executorService = Executors.newCachedThreadPool();

      // Reserve 3 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      groupCommitter.reserve("child-key-3");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])
      // - NormalGroup("0001", OPEN, slots:[Slot("child-key-3")])

      // Act

      List<Future<?>> futures = new ArrayList<>();
      // Mark the 2 slots in the first group as ready by putting values.
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey1, 11)));
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey2, 22)));
      executorService.shutdown();
      // Wait until they're done.
      for (Future<?> future : futures) {
        // All `ready()` method calls must throw an exception.
        Throwable cause = assertThrows(ExecutionException.class, future::get).getCause();
        assertThat(cause).isInstanceOf(GroupCommitException.class);
      }
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot("child-key-1"), Slot("child-key-2")])
      // - NormalGroup("0001", OPEN, slots:[Slot("child-key-3")])

      // Assert
      verify(failingEmitter).execute("0000", Arrays.asList(11, 22));
      verify(failingEmitter, never()).execute(eq("0001"), any());
    }
  }

  @Test
  void ready_WhenOnlyOneOfTwoSlotsIsReadyInNormalGroup_ShouldJustWait() throws Exception {
    // Arrange

    // `delayedSlotMoveTimeoutMillis` is enough long to wait `ready()`.
    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 2000)) {
      groupCommitter.setEmitter(emitter);
      ExecutorService executorService = Executors.newCachedThreadPool();

      // Reserve 3 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      groupCommitter.reserve("child-key-2");
      groupCommitter.reserve("child-key-3");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])
      // - NormalGroup("0001", OPEN, slots:[Slot("child-key-3")])

      // Act

      // Mark the first slot as ready by putting a value.
      Future<?> future = executorService.submit(() -> groupCommitter.ready(fullKey1, 11));
      executorService.shutdown();
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot(READY, "child-key-1"), Slot("child-key-2")])
      // - NormalGroup("0001", OPEN, slots:[Slot("child-key-3")])

      // Assert

      // The other slot is not ready and DelayedSlotMoveWorker hasn't moved it to a DelayedGroup
      // yet.
      // So, this timeout must happen.
      assertThrows(TimeoutException.class, () -> future.get(1000, TimeUnit.MILLISECONDS));
      verify(emitter, never()).execute(any(), any());
    }
  }

  @Test
  void ready_WhenSlotIsReadyInDelayedGroup_WithSuccessfulEmitTask_ShouldEmitThem()
      throws Exception {
    // Arrange
    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(emitter);
      ExecutorService executorService = Executors.newCachedThreadPool();

      // Reserve 2 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Prepare a DelayedGroup.
      List<Future<?>> futures = new ArrayList<>();
      // Mark the first slot in the first group as ready by putting a value.
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey1, 11)));
      // Wait for the move of the delayed second slot to a DelayedGroup.
      Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot(READY, "child-key-1")])
      // - DelayedGroup("0000:child-key-2", SIZE-FIXED, slots:[Slot("child-key-2")])
      verify(emitter).execute("0000", Collections.singletonList(11));
      verify(emitter, never()).execute(eq("0000:child-key-2"), any());

      // Act

      // Mark the second slot in the second group as ready by putting values.
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey2, 22)));
      executorService.shutdown();
      // Wait until they're done.
      for (Future<?> future : futures) {
        future.get();
      }
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot(READY, "child-key-1")])
      // - DelayedGroup("0000:child-key-2", DONE, slots:[Slot("child-key-2")])

      // Assert
      verify(emitter).execute("0000:child-key-2", Collections.singletonList(22));
    }
  }

  @Test
  void ready_WhenSlotIsReadyInDelayedGroup_WithFailingEmitTask_ShouldFail() throws Exception {
    // Arrange
    Emittable<String, Integer> failingEmitter =
        spy(
            // This should be an anonymous class since `spy()` can't handle a lambda.
            new Emittable<String, Integer>() {
              @Override
              public void execute(String key, List<Integer> values) {
                throw new RuntimeException("Something is wrong");
              }
            });

    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(failingEmitter);
      ExecutorService executorService = Executors.newCachedThreadPool();

      // Reserve 3 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Prepare a DelayedGroup.
      List<Future<?>> futures = new ArrayList<>();
      // Mark the first slot in the first group as ready by putting a value.
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey1, 11)));
      // Wait for the move of the delayed second slot to a DelayedGroup.
      Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot(READY, "child-key-1")])
      // - DelayedGroup("0000:child-key-2", SIZE-FIXED, slots:[Slot("child-key-2")])
      verify(failingEmitter).execute("0000", Collections.singletonList(11));
      verify(failingEmitter, never()).execute(eq("0000:child-key-2"), any());

      // Act

      // Mark the second slot in the second group as ready by putting values.
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey2, 22)));
      executorService.shutdown();
      // Wait until they're done.
      for (Future<?> future : futures) {
        // All `ready()` method calls must throw an exception.
        Throwable cause = assertThrows(ExecutionException.class, future::get).getCause();
        assertThat(cause).isInstanceOf(GroupCommitException.class);
      }
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot(READY, "child-key-1")])
      // - DelayedGroup("0000:child-key-2", DONE, slots:[Slot("child-key-2")])

      // Assert
      verify(failingEmitter).execute("0000:child-key-2", Collections.singletonList(22));
    }
  }

  @Test
  void remove_GivenKeyForOpenGroup_ShouldRemoveIt() throws Exception {
    // Arrange
    // `slotCapacity` is 3 to prevent the group from being size-fixed.
    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(3, 100, 400)) {
      groupCommitter.setEmitter(emitter);

      // Reserve 2 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", OPEN, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Act
      // Assert

      // Remove the 2 slots from the open group.
      groupCommitter.remove(fullKey1);
      groupCommitter.remove(fullKey2);
      // There should be no group at this moment.

      // The slots are already removed and these operations must fail.
      assertThrows(GroupCommitException.class, () -> groupCommitter.ready(fullKey1, 42));
      assertThrows(GroupCommitException.class, () -> groupCommitter.ready(fullKey2, 42));
      verify(emitter, never()).execute(any(), any());
    }
  }

  @Test
  void remove_GivenKeyForSizeFixedGroup_ShouldRemoveIt() throws Exception {
    // Arrange
    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(emitter);

      // Reserve 2 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Act
      // Assert

      // Remove the 2 slots from the size-fixed group.
      groupCommitter.remove(fullKey1);
      groupCommitter.remove(fullKey2);
      // There should be no group at this moment.

      // The slots are already removed and these operations must fail.
      assertThrows(GroupCommitException.class, () -> groupCommitter.ready(fullKey1, 42));
      assertThrows(GroupCommitException.class, () -> groupCommitter.ready(fullKey2, 42));
      verify(emitter, never()).execute(any(), any());
    }
  }

  @Test
  void remove_GivenKeyForReadyGroup_ShouldFail() throws Exception {
    // Arrange
    CountDownLatch countDownLatch = new CountDownLatch(1);
    Emittable<String, Integer> testableEmitter =
        spy(
            // This should be an anonymous class since `spy()` can't handle a lambda.
            new Emittable<String, Integer>() {
              @Override
              public void execute(String key, List<Integer> values) throws Exception {
                countDownLatch.await();
              }
            });

    try (GroupCommitter<String, String, String, String, Integer> groupCommitter =
        createGroupCommitter(2, 100, 400)) {
      groupCommitter.setEmitter(testableEmitter);
      ExecutorService executorService = Executors.newCachedThreadPool();

      // Reserve 2 slots.
      String fullKey1 = groupCommitter.reserve("child-key-1");
      String fullKey2 = groupCommitter.reserve("child-key-2");
      // There should be the following groups at this moment.
      // - NormalGroup("0000", SIZE-FIXED, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Mark the group as ready.
      List<Future<?>> futures = new ArrayList<>();
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey1, 11)));
      futures.add(executorService.submit(() -> groupCommitter.ready(fullKey2, 22)));
      executorService.shutdown();
      // Wait for a while so that the threads can wait on the latch.
      Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
      // There should be the following groups at this moment.
      // - NormalGroup("0000", READY, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Act
      // Assert

      // Remove the 2 slots, but it's too late, and they must not be removed.
      groupCommitter.remove(fullKey1);
      groupCommitter.remove(fullKey2);
      // There should be the following groups at this moment.
      // - NormalGroup("0000", READY, slots:[Slot("child-key-1"), Slot("child-key-2")])

      // Resume the emitter to get done.
      countDownLatch.countDown();
      for (Future<?> future : futures) {
        future.get();
      }
      // There should be the following groups at this moment.
      // - NormalGroup("0000", DONE, slots:[Slot("child-key-1"), Slot("child-key-2")])
      verify(testableEmitter).execute("0000", Arrays.asList(11, 22));
    }
  }
}