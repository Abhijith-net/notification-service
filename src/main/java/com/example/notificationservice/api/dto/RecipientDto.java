package com.example.notificationservice.api.dto;

import com.example.notificationservice.domain.ChannelType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class RecipientDto {

    @NotNull(message = "channel is required")
    private ChannelType channel;

    @NotBlank(message = "address is required")
    private String address;

    public ChannelType getChannel() { return channel; }
    public void setChannel(ChannelType channel) { this.channel = channel; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}
