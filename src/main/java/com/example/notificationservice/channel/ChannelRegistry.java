package com.example.notificationservice.channel;

import com.example.notificationservice.domain.ChannelType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ChannelRegistry {

    private final Map<ChannelType, NotificationChannel> channelsByType;

    public ChannelRegistry(List<NotificationChannel> channels) {
        this.channelsByType = channels.stream()
            .filter(NotificationChannel::isEnabled)
            .collect(Collectors.toUnmodifiableMap(NotificationChannel::getChannelType, c -> c));
    }

    public Optional<NotificationChannel> getChannel(ChannelType type) {
        return Optional.ofNullable(channelsByType.get(type));
    }

    public boolean supports(ChannelType type) {
        return channelsByType.containsKey(type);
    }

    public List<ChannelType> getSupportedChannels() {
        return List.copyOf(channelsByType.keySet());
    }
}
