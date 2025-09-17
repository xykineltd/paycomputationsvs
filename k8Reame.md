### Build docker image
```

         mvn -DskipTests spring-boot:build-image \
         -Ddocker.publishRegistry.username=kacytunde \
         -Ddocker.publishRegistry.password=dckr_pat_K9c-HdfboeduOqJRnVsLBkSvlMo \
         -Ddocker.publishRegistry.url=docker.io \
         -Dspring-boot.build-image.publish=true \
     	 -Dspring-boot.build-image.imageName=kacytunde/paycomputation-service:1.9

```

### run tests
./mvnw -Dtest=com.xykine.adminservice.domain.attendance.AttendanceServiceTest test

./mvnw -Dtest=com.xykine.adminservice.keycloak.KeycloakUserServiceIntegrationTest test

./mvnw -Dtest=com.xykine.adminservice.web.paymentinfo.PaymentInfoControllerIntegrationTest test
./mvnw -Dtest=com.xykine.adminservice.web.paymentinfo.PaymentInfoControllerTest test
mvn -Dtest=com.xykine.computation.ControllerIntegrationTest test 