### Build docker image
```

         mvn spring-boot:build-image \
         -Ddocker.publishRegistry.username=kacytunde \
         -Ddocker.publishRegistry.password=d \
         -Ddocker.publishRegistry.url=docker.io \
         -Dspring-boot.build-image.publish=true \
	 -Dspring-boot.build-image.imageName=kacytunde/paycomputation-service
	 -DskipTests
```