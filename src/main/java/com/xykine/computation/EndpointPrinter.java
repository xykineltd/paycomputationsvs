package com.xykine.computation;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Profile("Test")
public class EndpointPrinter {

    private final RequestMappingHandlerMapping handlerMapping;

    @Autowired
    public EndpointPrinter(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
    }

    @PostConstruct
    public void logAllEndpoints() {
        System.out.println("====== REGISTERED SPRING MVC ENDPOINTS ======");

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo mappingInfo = entry.getKey();
            HandlerMethod handlerMethod = entry.getValue();

            // Use pathPatternsCondition if available (Spring 6+), otherwise fall back
            Set<String> paths;

            if (mappingInfo.getPathPatternsCondition() != null) {
                paths = mappingInfo.getPathPatternsCondition().getPatterns()
                        .stream().map(PathPattern::getPatternString).collect(Collectors.toSet());
            } else if (mappingInfo.getPatternsCondition() != null) {
                paths = mappingInfo.getPatternsCondition().getPatterns();
            } else {
                paths = Set.of("N/A");
            }

            Set<RequestMethod> methods = mappingInfo.getMethodsCondition().getMethods();

            System.out.println("---------------------------------------------");
            System.out.println("Handler:   " + handlerMethod.getBeanType().getSimpleName() + "#" + handlerMethod.getMethod().getName());
            System.out.println("URL(s):    " + paths);
            System.out.println("Method(s): " + methods);
        }

        System.out.println("=============================================");
    }
}
