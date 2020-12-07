package io.stargate.db;

import io.stargate.config.store.api.ConfigStore;
import io.stargate.core.activator.BaseActivator;
import io.stargate.db.cdc.CDCService;
import io.stargate.db.cdc.CDCServiceFactory;
import io.stargate.db.datastore.DataStoreFactory;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nullable;

public class DbActivator extends BaseActivator {
  //  private ServicePointer<Metrics> metrics = ServicePointer.create(Metrics.class);
  //  private ServicePointer<CDCProducer> cdcProducer = ServicePointer.create(CDCProducer.class);
  private ServicePointer<ConfigStore> configStore = ServicePointer.create(ConfigStore.class);
  private ServicePointer<Persistence> persistence = ServicePointer.create(Persistence.class);
  private CDCService cdcService;

  public DbActivator() {
    super("CDC service");
  }

  @Nullable
  @Override
  protected ServiceAndProperties createService() {
    DataStoreFactory dataStoreFactory = new DataStoreFactory(configStore.get());
    CDCServiceFactory cdcServiceFactory =
        new CDCServiceFactory(configStore.get(), dataStoreFactory.create(persistence.get()));
    cdcServiceFactory.create();
    // todo when the KafkaCDCProducer will be plugged in.
    //    cdcService = new CDCServiceImpl(cdcProducer.get(), metrics.get(), configStore.get());
    return new ServiceAndProperties(dataStoreFactory, DataStoreFactory.class);
  }

  @Override
  protected void stopService() {
    try {
      if (cdcService != null) {
        cdcService.close();
      }
    } catch (Exception e) {
      throw new CDCCloseException("Problem when closing CDC service", e);
    }
  }

  @Override
  protected List<ServicePointer<?>> dependencies() {
    return Arrays.asList(configStore, persistence);
  }

  public static class CDCCloseException extends RuntimeException {
    public CDCCloseException(String message, Exception e) {
      super(message, e);
    }
  }
}
