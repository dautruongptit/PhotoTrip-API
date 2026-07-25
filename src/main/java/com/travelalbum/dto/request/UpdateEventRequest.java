package com.travelalbum.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateEventRequest {

    @NotBlank(message = "Event name is required")
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String description;
}
