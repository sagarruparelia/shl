package com.chanakya.shl.service;

import com.chanakya.shl.config.AppProperties;
import com.chanakya.shl.model.enums.FhirCategory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.signer.Aws4Signer;
import software.amazon.awssdk.auth.signer.params.Aws4SignerParams;
import software.amazon.awssdk.http.SdkHttpFullRequest;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.regions.Region;

import java.net.URI;
import java.util.List;

@Service
@Slf4j
public class HealthLakeService {

    private final AppProperties.HealthLakeProperties healthLakeProps;
    private final WebClient webClient;
    private final Aws4Signer signer;
    private final DefaultCredentialsProvider credentialsProvider;

    public HealthLakeService(AppProperties appProperties) {
        this.healthLakeProps = appProperties.getHealthlake();
        this.webClient = WebClient.builder().build();
        this.signer = Aws4Signer.create();
        this.credentialsProvider = DefaultCredentialsProvider.create();
    }

    public Mono<String> fetchBundle(String patientId, FhirCategory category) {
        return Mono.defer(() -> {
            validateConfigured();

            String endpoint = healthLakeProps.getDatastoreEndpoint();
            String resourcePath = "/r4/" + category.getResourceType();
            String queryParam = "patient=Patient/" + patientId;
            String fullUrl = endpoint + resourcePath + "?" + queryParam;

            URI uri = URI.create(fullUrl);

            SdkHttpFullRequest sdkRequest = SdkHttpFullRequest.builder()
                    .uri(uri)
                    .method(SdkHttpMethod.GET)
                    .build();

            Aws4SignerParams signerParams = Aws4SignerParams.builder()
                    .awsCredentials(credentialsProvider.resolveCredentials())
                    .signingName("healthlake")
                    .signingRegion(Region.of(healthLakeProps.getRegion()))
                    .build();

            SdkHttpFullRequest signedRequest = signer.sign(sdkRequest, signerParams);

            WebClient.RequestHeadersSpec<?> spec = webClient.get().uri(uri);
            signedRequest.headers().forEach((name, values) ->
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
