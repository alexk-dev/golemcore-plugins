package me.golemcore.plugins.golemcore.elevenlabs;

import me.golemcore.plugins.golemcore.elevenlabs.adapter.outbound.voice.ElevenLabsAdapter;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        ElevenLabsAdapter.class,
        ElevenLabsPluginSettingsContributor.class
})
public class ElevenLabsPluginConfiguration {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient();
    }
}
