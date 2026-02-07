plugins {
    java
    id("org.springframework.boot") version "4.0.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.chanakya"
version = "0.0.1-SNAPSHOT"
description = "shl"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

val nimbusJoseJwtVersion = "10.7"
val zxingVersion = "3.5.4"
val awsSdkVersion = "2.41.23"

dependencies {
    implementation(platform("software.amazon.awssdk:bom:$awsSdkVersion"))
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-json")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("com.nimbusds:nimbus-jose-jwt:$nimbusJoseJwtVersion")       // JWE encryption (alg:dir, enc:A256GCM)
    implementation("com.google.zxing:core:$zxingVersion")                      // QR code generation
    implementation("com.google.zxing:javase:$zxingVersion")
    implementation("software.amazon.awssdk:s3")                                // AWS S3 (reactive)
    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:sts")                               // IRSA support in EKS
    implementation("software.amazon.awssdk:http-auth-aws")                     // AwsV4HttpSigner
    implementation("software.amazon.awssdk:netty-nio-client")

    compileOnly("org.projectlombok:lombok")

    developmentOnly("org.springframework.boot:spring-boot-devtools")

    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-data-mongodb-reactive-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
