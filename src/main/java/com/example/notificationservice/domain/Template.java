package com.example.notificationservice.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "templates")
public class Template {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel_type", nullable = false)
    private ChannelType channelType;

    @Column(length = 10)
    private String locale = "en";

    @Column(name = "subject_template", length = 500)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, length = 10000)
    private String bodyTemplate;

    @Column(name = "extra", length = 2000)
    private String extra;

    @Column(nullable = false)
    private boolean active = true;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ChannelType getChannelType() { return channelType; }
    public void setChannelType(ChannelType channelType) { this.channelType = channelType; }
    public String getLocale() { return locale; }
    public void setLocale(String locale) { this.locale = locale; }
    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }
    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }
    public String getExtra() { return extra; }
    public void setExtra(String extra) { this.extra = extra; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
