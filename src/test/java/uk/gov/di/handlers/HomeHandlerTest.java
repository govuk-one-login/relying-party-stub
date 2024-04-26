package uk.gov.di.handlers;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import spark.Request;
import spark.Response;
import uk.gov.di.config.Configuration;
import uk.gov.di.config.RPConfig;
import uk.gov.di.utils.ViewHelper;

import java.util.HashSet;

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
                        mockStatic(Configuration.class);
                MockedStatic<ViewHelper> viewHelperMock = mockStatic(ViewHelper.class)) {
            var relyingPartyConfigMock = mock(RPConfig.class);
            configurationMockedStatic
                    .when(() -> Configuration.getRelyingPartyConfig(null))
                    .thenReturn(relyingPartyConfigMock);
            when(relyingPartyConfigMock.serviceName()).thenReturn("Test Service");
            when(relyingPartyConfigMock.clientType()).thenReturn("web");

            var mockRequest = mock(Request.class);
            var mockResponse = mock(Response.class);

            homeHandler.handle(mockRequest, mockResponse);

            viewHelperMock.verify(() -> ViewHelper.render(any(), eq("home.mustache")));
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

            var mockRequest = mock(Request.class);
            var mockResponse = mock(Response.class);

            when(mockRequest.queryParams())
                    .thenReturn(
                            new HashSet<>() {
                                {
                                    add("relyingParty");
                                }
                            });
            when(mockRequest.queryParams("relyingParty")).thenReturn("testRpValue");

            homeHandler.handle(mockRequest, mockResponse);

            verify(mockResponse)
                    .cookie(
                            eq("/"),
                            eq("relyingParty"),
                            eq("testRpValue"),
                            eq(3600),
                            eq(false),
                            eq(true));
        }
    }
}
