package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import com.chanakya.shl.model.enums.FhirCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.auth.aws.signer.AwsV4HttpSigner;
import software.amazon.awssdk.http.auth.spi.signer.SignedRequest;

import java.net.URI;
import java.util.List;

@Service
@Slf4j
public class HealthLakeService {

    private final AppProperties.HealthLakeProperties healthLakeProps;
    private final WebClient webClient;
    private final AwsV4HttpSigner signer;
    private final DefaultCredentialsProvider credentialsProvider;

    public HealthLakeService(AppProperties appProperties,
                             DefaultCredentialsProvider credentialsProvider) {
        this.healthLakeProps = appProperties.getHealthlake();
        this.webClient = WebClient.builder().build();
        this.signer = AwsV4HttpSigner.create();
        this.credentialsProvider = credentialsProvider;
    }

    public Mono<String> fetchBundle(String patientId, FhirCategory category) {
        return Mono.defer(() -> {
            validateConfigured();

            String endpoint = healthLakeProps.getDatastoreEndpoint();
            String resourcePath = "/r4/" + category.getResourceType();
            String queryParam = "patient=Patient/" + patientId;
            String fullUrl = endpoint + resourcePath + "?" + queryParam;

            URI uri = URI.create(fullUrl);

            SdkHttpRequest httpRequest = SdkHttpRequest.builder()
                    .uri(uri)
                    .method(SdkHttpMethod.GET)
                    .build();

            SignedRequest signedRequest = signer.sign(r -> r
                    .identity(credentialsProvider.resolveCredentials())
                    .request(httpRequest)
                    .putProperty(AwsV4HttpSigner.SERVICE_SIGNING_NAME, "healthlake")
                    .putProperty(AwsV4HttpSigner.REGION_NAME, healthLakeProps.getRegion()));

            WebClient.RequestHeadersSpec<?> spec = webClient.get().uri(uri);
            signedRequest.request().headers().forEach((name, values) ->
                    values.forEach(value -> spec.header(name, value)));

            log.debug("Fetching {} for patient {} from HealthLake", category.getDisplayName(), patientId);

            return spec.retrieve()
                    .bodyToMono(String.class)
                    .doOnSuccess(body -> log.debug("Fetched {} bundle for patient {}", category.getDisplayName(), patientId))
                    .doOnError(err -> log.error("Failed to fetch {} for patient {}: {}", category.getDisplayName(), patientId, err.getMessage()));
        });
    }

    public Flux<String> fetchBundles(String patientId, List<FhirCategory> categories) {
        return Flux.fromIterable(categories)
                .flatMap(category -> fetchBundle(patientId, category));
    }

    private void validateConfigured() {
        String endpoint = healthLakeProps.getDatastoreEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalStateException("HealthLake datastore endpoint is not configured. Set HEALTHLAKE_ENDPOINT environment variable.");
        }
    }
}
