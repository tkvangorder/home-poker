package org.homepoker.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@c")
@JsonPropertyOrder({"@c"}) // serialize type info first
@JsonIgnoreProperties(ignoreUnknown = true)
public interface Event {
}
