package evans18.assignment.streamprocessorfactory.impl;

import com.google.auto.service.AutoService;
import evans18.assignment.streamprocessorfactory.api.EventProcessorFactory;
import evans18.assignment.streamprocessorfactory.api.StreamProcessor;

import java.time.Duration;

@AutoService(EventProcessorFactory.class)
public final class PickingEventProcessorFactory implements EventProcessorFactory {
    @Override
    public StreamProcessor createProcessor(int maxEvents, Duration maxTime) {
        throw new UnsupportedOperationException(
                "Please implement me; see README.md for the specification");
    }

}