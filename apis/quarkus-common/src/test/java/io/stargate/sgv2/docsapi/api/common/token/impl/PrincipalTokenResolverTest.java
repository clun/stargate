/*
 * Copyright The Stargate Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.stargate.sgv2.docsapi.api.common.token.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableMap;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.stargate.sgv2.docsapi.api.common.token.CassandraTokenResolver;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import javax.enterprise.inject.Instance;
import javax.inject.Inject;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.lang3.RandomStringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(PrincipalTokenResolverTest.Profile.class)
class PrincipalTokenResolverTest {

  public static final class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return ImmutableMap.<String, String>builder()
          .put("stargate.auth.token-resolver.type", "principal")
          .build();
    }
  }

  @Inject Instance<CassandraTokenResolver> tokenResolver;

  SecurityContext securityContext;

  @BeforeEach
  public void setup() {
    // TODO we should be able to do this directly on the field with @InjectMock, but for some reason
    //  it doesn't work
    securityContext = mock(SecurityContext.class);
    Principal principal = mock(Principal.class);
    when(securityContext.getUserPrincipal()).thenReturn(principal);
  }

  @Nested
  class Resolve {

    @Test
    public void happyPath() {
      String token = RandomStringUtils.randomAlphanumeric(16);
      when(securityContext.getUserPrincipal().getName()).thenReturn(token);

      Optional<String> result = tokenResolver.get().resolve(null, securityContext);

      Assertions.assertThat(result).contains(token);
    }

    @Test
    public void noPrincipal() {
      when(securityContext.getUserPrincipal()).thenReturn(null);

      Optional<String> result = tokenResolver.get().resolve(null, securityContext);

      Assertions.assertThat(result).isEmpty();
    }
  }
}
