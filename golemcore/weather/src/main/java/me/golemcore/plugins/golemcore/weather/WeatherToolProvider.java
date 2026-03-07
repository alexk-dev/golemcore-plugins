package me.golemcore.plugins.golemcore.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import me.golemcore.plugin.api.extension.model.ToolDefinition;
import me.golemcore.plugin.api.extension.model.ToolResult;
import me.golemcore.plugin.api.extension.spi.ToolProvider;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class WeatherToolProvider implements ToolProvider {

    private static final String PARAM_LOCATION = "location";
    private static final String SCHEMA_TYPE = "type";
    private static final String SCHEMA_OBJECT = "object";
    private static final String SCHEMA_STRING = "string";
    private static final String SCHEMA_PROPERTIES = "properties";
    private static final String SCHEMA_REQUIRED = "required";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public WeatherToolProvider() {
        this.httpClient = new OkHttpClient();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("weather")
                .description("Get current weather for a location using Open-Meteo.")
                .inputSchema(Map.of(
                        SCHEMA_TYPE, SCHEMA_OBJECT,
                        SCHEMA_PROPERTIES, Map.of(
                                PARAM_LOCATION, Map.of(
                                        SCHEMA_TYPE, SCHEMA_STRING,
                                        "description", "City name or location (e.g. London, New York, Tokyo)")),
                        SCHEMA_REQUIRED, List.of(PARAM_LOCATION)))
                .build();
    }

    @Override
    public CompletableFuture<ToolResult> execute(Map<String, Object> parameters) {
        return CompletableFuture.supplyAsync(() -> {
            String location = parameters.get(PARAM_LOCATION) instanceof String value ? value : null;
            if (location == null || location.isBlank()) {
                return ToolResult.failure("Location is required");
            }
            try {
                GeocodingResponse geocoding = geocode(location);
                if (geocoding.results == null || geocoding.results.isEmpty()) {
                    return ToolResult.failure("Location not found: " + location);
                }

                GeoResult geo = geocoding.results.getFirst();
                WeatherResponse weather = getCurrentWeather(geo.latitude, geo.longitude);
                if (weather.currentWeather == null) {
                    return ToolResult.failure("Weather data not available");
                }

                CurrentWeather current = weather.currentWeather;
                String description = getWeatherDescription(current.weatherCode);
                String output = String.format(
                        "Weather in %s, %s:%n- Conditions: %s%n- Temperature: %.1f°C%n- Humidity: %.0f%%%n- Wind Speed: %.1f km/h",
                        geo.name,
                        geo.country,
                        description,
                        current.temperature,
                        current.humidity,
                        current.windSpeed);

                return ToolResult.success(output, Map.of(
                        "location", geo.name,
                        "country", geo.country,
                        "temperature_celsius", current.temperature,
                        "humidity_percent", current.humidity,
                        "wind_speed_kmh", current.windSpeed,
                        "weather_code", current.weatherCode,
                        "description", description));
            } catch (Exception e) {
                return ToolResult.failure("Failed to get weather: " + e.getMessage());
            }
        });
    }

    private GeocodingResponse geocode(String location) throws IOException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("geocoding-api.open-meteo.com")
                .addPathSegments("v1/search")
                .addQueryParameter("name", location)
                .addQueryParameter("count", "1")
                .build();
        return execute(url, GeocodingResponse.class);
    }

    private WeatherResponse getCurrentWeather(double latitude, double longitude) throws IOException {
        HttpUrl url = new HttpUrl.Builder()
                .scheme("https")
                .host("api.open-meteo.com")
                .addPathSegments("v1/forecast")
                .addQueryParameter("latitude", Double.toString(latitude))
                .addQueryParameter("longitude", Double.toString(longitude))
                .addQueryParameter("current_weather", "true")
                .addQueryParameter("current", "temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code")
                .build();
        return execute(url, WeatherResponse.class);
    }

    private <T> T execute(HttpUrl url, Class<T> type) throws IOException {
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = httpClient.newCall(request).execute();
                ResponseBody body = response.body()) {
            if (!response.isSuccessful() || body == null) {
                throw new IOException("HTTP " + response.code());
            }
            return objectMapper.readValue(body.string(), type);
        }
    }

    private String getWeatherDescription(int code) {
        return switch (code) {
        case 0 -> "Clear sky";
        case 1, 2, 3 -> "Partly cloudy";
        case 45, 48 -> "Foggy";
        case 51, 53, 55 -> "Drizzle";
        case 61, 63, 65 -> "Rain";
        case 66, 67 -> "Freezing rain";
        case 71, 73, 75 -> "Snow";
        case 77 -> "Snow grains";
        case 80, 81, 82 -> "Rain showers";
        case 85, 86 -> "Snow showers";
        case 95 -> "Thunderstorm";
        case 96, 99 -> "Thunderstorm with hail";
        default -> "Unknown";
        };
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeocodingResponse {
        @JsonProperty("results")
        private List<GeoResult> results;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class GeoResult {
        @JsonProperty("name")
        private String name;

        @JsonProperty("country")
        private String country;

        @JsonProperty("latitude")
        private double latitude;

        @JsonProperty("longitude")
        private double longitude;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class WeatherResponse {
        @JsonProperty("current_weather")
        private CurrentWeather currentWeather;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class CurrentWeather {
        @JsonProperty("temperature")
        private double temperature;

        @JsonProperty("relative_humidity_2m")
        private double humidity;

        @JsonProperty("wind_speed_10m")
        private double windSpeed;

        @JsonProperty("weathercode")
        private int weatherCode;
    }
}
