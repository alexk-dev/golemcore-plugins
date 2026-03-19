package me.golemcore.plugins.golemcore.slack.support;

import com.slack.api.Slack;
import com.slack.api.bolt.socket_mode.SocketModeApp;
import com.slack.api.methods.MethodsClient;
import com.slack.api.methods.request.apps.connections.AppsConnectionsOpenRequest;
import com.slack.api.methods.request.auth.AuthTestRequest;
import com.slack.api.methods.response.apps.connections.AppsConnectionsOpenResponse;
import com.slack.api.methods.response.auth.AuthTestResponse;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SlackBoltSocketGatewayTest {

    @Test
    void shouldFailFastWhenAppTokenIsInvalidForSocketMode() throws Exception {
        Slack slack = mock(Slack.class);
        MethodsClient botMethods = mock(MethodsClient.class);
        MethodsClient appMethods = mock(MethodsClient.class);

        AuthTestResponse authResponse = new AuthTestResponse();
        authResponse.setOk(true);
        authResponse.setUserId("U123");

        AppsConnectionsOpenResponse appTokenResponse = new AppsConnectionsOpenResponse();
        appTokenResponse.setOk(false);
        appTokenResponse.setError("invalid_auth");

        when(slack.methods("xoxb-test")).thenReturn(botMethods);
        when(slack.methods("xapp-test")).thenReturn(appMethods);
        when(botMethods.authTest(any(AuthTestRequest.class))).thenReturn(authResponse);
        when(appMethods.appsConnectionsOpen(any(AppsConnectionsOpenRequest.class))).thenReturn(appTokenResponse);

        try (MockedStatic<Slack> slackStatic = mockStatic(Slack.class);
                MockedConstruction<SocketModeApp> socketModeApps = mockConstruction(SocketModeApp.class)) {
            slackStatic.when(Slack::getInstance).thenReturn(slack);

            SlackBoltSocketGateway gateway = new SlackBoltSocketGateway();

            IllegalStateException error = assertThrows(
                    IllegalStateException.class,
                    () -> gateway.connect("xapp-test", "xoxb-test", envelope -> {
                    }, envelope -> {
                    }));

            assertTrue(error.getMessage().contains("invalid_auth"));
            assertFalse(gateway.isConnected());
            assertTrue(socketModeApps.constructed().isEmpty());
            verify(botMethods).authTest(any(AuthTestRequest.class));
            verify(appMethods).appsConnectionsOpen(any(AppsConnectionsOpenRequest.class));
        }
    }
}
