package br.com.ronybrand.orderapi.commons.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

/**
 * GraalVM native-image reflection hints covering the CGLIB proxies SpringDoc creates internally
 * because of {@code @Lazy} fields in the library itself - without this, those classes are
 * unreachable via reflection in a native image and Swagger/OpenAPI breaks at native runtime even
 * though it compiles cleanly. Registered via {@code @ImportRuntimeHints} on the application's
 * main class.
 */
@Slf4j
public class SpringDocRuntimeHints implements RuntimeHintsRegistrar {

    private static final String[] REFLECTIVE_CLASSES = {
            "org.springdoc.core.providers.SpringWebProvider",
            "org.springdoc.core.providers.ActuatorProvider",
            "org.springdoc.core.providers.CloudFunctionProvider",
            "org.springdoc.core.providers.ObjectMapperProvider",
            "org.springdoc.core.providers.RepositoryRestConfigurationProvider",
            "org.springdoc.core.providers.RouterFunctionProvider",
            "org.springdoc.core.providers.SecurityOAuth2Provider",
            "org.springdoc.core.providers.SpringDocProviders",
            "org.springdoc.core.providers.WebConversionServiceProvider",
            "org.springdoc.core.providers.SpringWebProvider$$SpringCGLIB$$0",
            "org.springdoc.core.providers.ActuatorProvider$$SpringCGLIB$$0",
            "org.springdoc.core.providers.ObjectMapperProvider$$SpringCGLIB$$0",
            "org.springdoc.core.providers.RepositoryRestConfigurationProvider$$SpringCGLIB$$0",
            "org.springdoc.core.providers.SecurityOAuth2Provider$$SpringCGLIB$$0",
            "org.springdoc.webmvc.core.providers.SpringWebMvcProvider",
            "org.springdoc.webmvc.core.providers.ActuatorWebMvcProvider",
            "org.springdoc.webmvc.core.providers.RouterFunctionWebMvcProvider",
            "org.springdoc.webmvc.core.providers.SpringWebMvcProvider$$SpringCGLIB$$0",
            "org.springdoc.webmvc.core.providers.ActuatorWebMvcProvider$$SpringCGLIB$$0",
            "org.springdoc.webmvc.core.providers.RouterFunctionWebMvcProvider$$SpringCGLIB$$0",
            "org.springdoc.webmvc.ui.SwaggerWelcomeWebMvc",
            "org.springdoc.webmvc.ui.SwaggerConfigResource",
            "org.springdoc.webmvc.ui.SwaggerWelcomeCommon",
            "org.springdoc.ui.AbstractSwaggerWelcome",
            "org.springdoc.core.configuration.SpringDocConfiguration",
            "org.springdoc.core.configuration.SpringDocUIConfiguration",
            "org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration",
            "org.springdoc.webmvc.ui.SwaggerConfig",
            "org.springdoc.core.service.AbstractRequestService",
            "org.springdoc.core.service.GenericParameterService",
            "org.springdoc.core.service.GenericResponseService",
            "org.springdoc.core.service.OpenAPIService",
            "org.springdoc.core.service.OperationService",
            "org.springdoc.core.service.RequestBodyService",
            "org.springdoc.core.service.SecurityService",
            "org.springdoc.core.properties.SpringDocConfigProperties",
            "org.springdoc.core.properties.SwaggerUiConfigParameters",
            "org.springdoc.core.properties.SwaggerUiConfigProperties",
            "org.springdoc.core.properties.SwaggerUiOAuthProperties",
            "org.springdoc.core.customizers.OpenApiBuilderCustomizer",
            "org.springdoc.core.customizers.OperationCustomizer",
            "org.springdoc.core.customizers.RouterOperationCustomizer",
            "org.springdoc.core.customizers.ServerBaseUrlCustomizer",
            "org.springdoc.webmvc.ui.SwaggerIndexTransformer",
            "org.springdoc.webmvc.ui.SwaggerIndexPageTransformer",
    };

    @Override
    public void registerHints(final RuntimeHints hints, final ClassLoader classLoader) {
        for (final String className : REFLECTIVE_CLASSES) {
            registerReflectiveType(hints, className);
        }
    }

    private void registerReflectiveType(final RuntimeHints hints, final String className) {
        try {
            hints.reflection().registerType(TypeReference.of(className),
                    MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                    MemberCategory.INVOKE_DECLARED_METHODS,
                    MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS,
                    MemberCategory.INVOKE_PUBLIC_METHODS);
        } catch (final IllegalArgumentException | IllegalStateException e) {
            log.debug("Skipping AOT hint registration for unavailable class: {}", className, e);
        }
    }
}
