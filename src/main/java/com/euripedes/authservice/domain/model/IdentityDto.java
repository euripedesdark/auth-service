package com.euripedes.authservice.domain.model;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
public class IdentityDto {
    private String identityId;
    private String username;
    private String provider;
    private List<String> groups;
}
