// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: scalardb.proto

package com.scalar.db.rpc;

public interface TransactionalScanRequestOrBuilder extends
    // @@protoc_insertion_point(interface_extends:rpc.TransactionalScanRequest)
    com.google.protobuf.MessageOrBuilder {

  /**
   * <code>string transaction_id = 1;</code>
   * @return The transactionId.
   */
  java.lang.String getTransactionId();
  /**
   * <code>string transaction_id = 1;</code>
   * @return The bytes for transactionId.
   */
  com.google.protobuf.ByteString
      getTransactionIdBytes();

  /**
   * <code>.rpc.Scan scan = 2;</code>
   * @return Whether the scan field is set.
   */
  boolean hasScan();
  /**
   * <code>.rpc.Scan scan = 2;</code>
   * @return The scan.
   */
  com.scalar.db.rpc.Scan getScan();
  /**
   * <code>.rpc.Scan scan = 2;</code>
   */
  com.scalar.db.rpc.ScanOrBuilder getScanOrBuilder();
}