package com.alexjck.plugins.conditional.alerts;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.graylog2.Configuration;
import org.graylog2.alerts.AbstractAlertCondition;
import org.graylog2.indexer.results.CountResult;
import org.graylog2.indexer.results.ResultMessage;
import org.graylog2.indexer.results.SearchResult;
import org.graylog2.indexer.searches.Searches;
import org.graylog2.indexer.searches.Sorting;
import org.graylog2.plugin.Message;
import org.graylog2.plugin.MessageSummary;
import org.graylog2.plugin.Tools;
import org.graylog2.plugin.alarms.AlertCondition;
import org.graylog2.plugin.configuration.ConfigurationRequest;
import org.graylog2.plugin.configuration.fields.ConfigurationField;
import org.graylog2.plugin.configuration.fields.DropdownField;
import org.graylog2.plugin.configuration.fields.NumberField;
import org.graylog2.plugin.configuration.fields.TextField;
import org.graylog2.plugin.indexer.searches.timeranges.AbsoluteRange;
import org.graylog2.plugin.indexer.searches.timeranges.InvalidRangeParametersException;
import org.graylog2.plugin.indexer.searches.timeranges.RelativeRange;
import org.graylog2.plugin.streams.Stream;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

public class ConditionalCountAlertCondition extends AbstractAlertCondition {
    private static final Logger LOG = LoggerFactory.getLogger(ConditionalCountAlertCondition.class);

    enum ThresholdType {

        MORE("more than"),
        LESS("less than");

        private final String description;

        ThresholdType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
    
    private final Searches searches;
    @SuppressWarnings("unused")
	private final Configuration configuration;
    private final String query;
    private final int time;
    private final ThresholdType thresholdType;
    private final int threshold;

    public interface Factory extends AlertCondition.Factory {
        @Override
        ConditionalCountAlertCondition create(Stream stream,
                                               @Assisted("id") String id,
                                               DateTime createdAt,
                                               @Assisted("userid") String creatorUserId,
                                               Map<String, Object> parameters,
                                               @Assisted("title") @Nullable String title);

        @Override
        Config config();

        @Override
        Descriptor descriptor();
    }

    public static class Config implements AlertCondition.Config {
        public Config() {
        }

        @Override
        public ConfigurationRequest getRequestedConfiguration() {
            final ConfigurationRequest configurationRequest = ConfigurationRequest.createWithFields(
                    new TextField("query", "Query", "", "Query that should be checked", ConfigurationField.Optional.NOT_OPTIONAL),
                    new NumberField("time", "Time Range", 5, "Evaluate the condition for all messages received in the given number of minutes", ConfigurationField.Optional.NOT_OPTIONAL),
                    new DropdownField(
                            "threshold_type",
                            "Threshold Type",
                            ThresholdType.MORE.toString(),
                            Arrays.stream(ThresholdType.values()).collect(Collectors.toMap(Enum::toString, ThresholdType::getDescription)),
                            "Select condition to trigger alert: when there are more or less messages than the threshold",
                            ConfigurationField.Optional.NOT_OPTIONAL),
                    new NumberField("threshold", "Threshold", 0.0, "Value which triggers an alert if crossed", ConfigurationField.Optional.NOT_OPTIONAL)
            );
            configurationRequest.addFields(AbstractAlertCondition.getDefaultConfigurationFields());
            return configurationRequest;
        }
    }

    public static class Descriptor extends AlertCondition.Descriptor {
        public Descriptor() {
            super(
                "Message Conditional Count Alert Condition",
                "https://github.com/alcampos/graylog-plugin-alert-conditional-count",
                "This condition is triggered when there are more or less messages (matching a defined query) than the threshold."
            );
        }
    }

	@AssistedInject
    public ConditionalCountAlertCondition(Searches searches,
                                           Configuration configuration,
                                           @Assisted Stream stream,
                                           @Nullable @Assisted("id") String id,
                                           @Assisted DateTime createdAt,
                                           @Assisted("userid") String creatorUserId,
                                           @Assisted Map<String, Object> parameters,
                                           @Assisted("title") @Nullable String title) {
        super(stream, id, ConditionalCountAlertCondition.class.getCanonicalName(), createdAt, creatorUserId, parameters, title);
        this.searches = searches;
        this.configuration = configuration;
        this.query = (String) parameters.get("query");
        this.time = Tools.getNumber(parameters.get("time"), 5).intValue();

        final String thresholdType = (String) parameters.get("threshold_type");
        final String upperCaseThresholdType = thresholdType.toUpperCase(Locale.ENGLISH);

        if (!thresholdType.equals(upperCaseThresholdType)) {
            final HashMap<String, Object> updatedParameters = new HashMap<>();
            updatedParameters.putAll(parameters);
            updatedParameters.put("threshold_type", upperCaseThresholdType);
            super.setParameters(updatedParameters);
        }
        this.thresholdType = ThresholdType.valueOf(upperCaseThresholdType);
        this.threshold = Tools.getNumber(parameters.get("threshold"), 0).intValue();
    }

    @Override
    public CheckResult runCheck() {       
		try {
			final RelativeRange relativeRange = RelativeRange.create(time * 60);
	        final AbsoluteRange range = AbsoluteRange.create(relativeRange.getFrom(), relativeRange.getTo());
	        final String filter = "streams:" + stream.getId();
	        final CountResult result = searches.count(query, range, filter);
	        final long count = result.count();
	        
            final boolean triggered;
            switch (thresholdType) {
                case MORE:
                    triggered = count > threshold;
                    break;
                case LESS:
                    triggered = count < threshold;
                    break;
                default:
                    triggered = false;
            }
            
            if (triggered) {
                final List<MessageSummary> summaries = Lists.newArrayList();
                if (getBacklog() > 0) {
                    final SearchResult backlogResult = searches.search(query, filter,
                        range, getBacklog(), 0, new Sorting(Message.FIELD_TIMESTAMP, Sorting.Direction.DESC));
                    for (ResultMessage resultMessage : backlogResult.getResults()) {
                        final Message msg = resultMessage.getMessage();
                        summaries.add(new MessageSummary(resultMessage.getIndex(), msg));
                    }
                }
                final String resultDescription = "Stream had " + count + " messages in the last " + time
                        + " minutes with trigger condition " + thresholdType.toString().toLowerCase(Locale.ENGLISH)
                        + " than " + threshold + " messages. " + "(Current grace time: " + grace + " minutes)";
                return new CheckResult(true, this, resultDescription, Tools.nowUTC(), summaries);
            } else {
			    LOG.debug("Alert check <{}> returned no results.", id);
			    return new NegativeCheckResult();
            }
		} catch (InvalidRangeParametersException e) {
            LOG.error("Invalid timerange.", e);
            return null;
		}
    }

    @Override
    public String getDescription() {
        return "time: " + time
                + ", threshold_type: " + thresholdType.toString().toLowerCase(Locale.ENGLISH)
                + ", threshold: " + threshold
                + ", grace: " + grace
                + ", query: " + query
                + ", repeat notifications: " + repeatNotifications;
    }
}