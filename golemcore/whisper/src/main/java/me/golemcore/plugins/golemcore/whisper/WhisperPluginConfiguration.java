package me.golemcore.plugins.golemcore.whisper;

import me.golemcore.plugins.golemcore.whisper.adapter.outbound.voice.WhisperCompatibleSttAdapter;
import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackageClasses = {
        WhisperCompatibleSttAdapter.class,
        WhisperPluginSettingsContributor.class
})
public class WhisperPluginConfiguration {

    @Bean
    public OkHttpClient okHttpClient() {
        return new OkHttpClient();
    }
}
