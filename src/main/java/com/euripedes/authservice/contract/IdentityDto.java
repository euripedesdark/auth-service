package com.euripedes.authservice.contract;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(name="Identity")
public record IdentityDto(String identityId,String username,String provider,List<String> groups){}
