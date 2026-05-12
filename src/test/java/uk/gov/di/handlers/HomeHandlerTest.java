package uk.gov.di.handlers;

import io.javalin.http.Context;
import io.javalin.http.Cookie;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import uk.gov.di.config.Configuration;
import uk.gov.di.config.RPConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HomeHandlerTest {
    private static final HomeHandler homeHandler = new HomeHandler();

    @Test
    void homeHandlerRendersWebScreenIfClientTypeIsWeb() {
        try (MockedStatic<Configuration> configurationMockedStatic =
                mockStatic(Configuration.class)) {
            var relyingPartyConfigMock = mock(RPConfig.class);
            configurationMockedStatic
                    .when(() -> Configuration.getRelyingPartyConfig(null))
                    .thenReturn(relyingPartyConfigMock);
            when(relyingPartyConfigMock.serviceName()).thenReturn("Test Service");
            when(relyingPartyConfigMock.clientType()).thenReturn("web");

            var mockCtx = mock(Context.class);

            homeHandler.handle(mockCtx);

            verify(mockCtx).render(eq("/home.mustache"), any());
        }
    }

    @Test
    void shouldUseRpFromUrlParametersAndCreateCookie() {
        try (MockedStatic<Configuration> configurationMockedStatic =
                mockStatic(Configuration.class)) {
            var relyingPartyConfigMock = mock(RPConfig.class);
            configurationMockedStatic
                    .when(() -> Configuration.getRelyingPartyConfig("testRpValue"))
                    .thenReturn(relyingPartyConfigMock);
            when(relyingPartyConfigMock.serviceName()).thenReturn("Test Service");
            when(relyingPartyConfigMock.clientType()).thenReturn("web");

            var mockCtx = mock(Context.class);
            when(mockCtx.queryParam("relyingParty")).thenReturn("testRpValue");

            homeHandler.handle(mockCtx);

            ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
            verify(mockCtx).cookie(cookieCaptor.capture());
            Cookie cookie = cookieCaptor.getValue();
            assertEquals("relyingParty", cookie.getName());
            assertEquals("testRpValue", cookie.getValue());
            assertEquals("/", cookie.getPath());
            assertEquals(3600, cookie.getMaxAge());
            assertEquals(true, cookie.isHttpOnly());
        }
    }
}
