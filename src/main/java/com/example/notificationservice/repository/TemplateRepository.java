package com.example.notificationservice.repository;

import com.example.notificationservice.domain.ChannelType;
import com.example.notificationservice.domain.Template;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TemplateRepository extends JpaRepository<Template, String> {

    Optional<Template> findByIdAndActiveTrue(String id);

    List<Template> findByChannelTypeAndActiveTrue(ChannelType channelType);

    Optional<Template> findByIdAndLocaleAndActiveTrue(String id, String locale);
}
