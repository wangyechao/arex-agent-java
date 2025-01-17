package io.arex.inst.runtime.service;

import io.arex.agent.bootstrap.model.MockStrategyEnum;

public class DataService {

    public static DataService INSTANCE;

    public static Builder builder() {
        return new Builder();
    }

    private final DataCollector saver;

    DataService(DataCollector dataSaver) {
        this.saver = dataSaver;
    }

    public void save(String data) {
        saver.save(data);
    }

    public String query(String data, MockStrategyEnum mockStrategy) {
        return saver.query(data, mockStrategy);
    }

    public static class Builder {

        private DataCollector collector;

        public Builder setDataCollector(DataCollector collector) {
            this.collector = collector;
            return this;
        }

        public void build() {
            INSTANCE = new DataService(this.collector);
        }
    }
}
