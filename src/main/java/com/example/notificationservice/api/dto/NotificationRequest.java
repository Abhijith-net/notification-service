package com.example.notificationservice.api.dto;

import com.example.notificationservice.domain.ChannelType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Map;

public class NotificationRequest {

    @NotBlank(message = "templateId is required")
    private String templateId;

    @NotEmpty(message = "channels are required")
    private List<ChannelType> channels;

    @NotEmpty(message = "recipients are required")
    @Valid
    private List<RecipientDto> recipients;

    private Map<String, String> variables;

    private String priority = "NORMAL";

    private String scheduledAt;

    public String getTemplateId() { return templateId; }
    public void setTemplateId(String templateId) { this.templateId = templateId; }
    public List<ChannelType> getChannels() { return channels; }
    public void setChannels(List<ChannelType> channels) { this.channels = channels; }
    public List<RecipientDto> getRecipients() { return recipients; }
    public void setRecipients(List<RecipientDto> recipients) { this.recipients = recipients; }
    public Map<String, String> getVariables() { return variables; }
    public void setVariables(Map<String, String> variables) { this.variables = variables; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(String scheduledAt) { this.scheduledAt = scheduledAt; }
}
