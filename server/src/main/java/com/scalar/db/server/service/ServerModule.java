package com.scalar.db.server.service;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.scalar.db.api.DistributedStorage;
import com.scalar.db.api.DistributedStorageAdmin;
import com.scalar.db.api.DistributedTransactionManager;
import com.scalar.db.config.DatabaseConfig;
import com.scalar.db.server.GateKeeper;
import com.scalar.db.server.Metrics;
import com.scalar.db.server.config.ServerConfig;
import com.scalar.db.service.StorageFactory;
import com.scalar.db.service.TransactionFactory;

public class ServerModule extends AbstractModule {

  private final ServerConfig config;
  private final StorageFactory storageFactory;
  private final TransactionFactory transactionFactory;

  public ServerModule(ServerConfig config, DatabaseConfig databaseConfig) {
    this.config = config;
    storageFactory = new StorageFactory(databaseConfig);
    transactionFactory = new TransactionFactory(databaseConfig);
  }

  @Override
  protected void configure() {
    bind(GateKeeper.class).to(config.getGateKeeperClass()).in(Singleton.class);
  }

  @Provides
  @Singleton
  DistributedStorage provideDistributedStorage() {
    return storageFactory.getStorage();
  }

  @Provides
  @Singleton
  DistributedStorageAdmin provideDistributedStorageAdmin() {
    return storageFactory.getAdmin();
  }

  @Provides
  @Singleton
  DistributedTransactionManager provideDistributedTransactionManager() {
    return transactionFactory.getTransactionManager();
  }

  @Provides
  @Singleton
  Metrics provideMetrics() {
    return new Metrics(config);
  }
}
