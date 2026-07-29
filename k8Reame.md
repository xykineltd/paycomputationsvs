### Build docker image

Use Docker Hub credentials from environment variables (never commit PATs):

```bash
export DOCKER_USERNAME=your-dockerhub-username
export DOCKER_PASSWORD=your-dockerhub-token   # create via Docker Hub Access Tokens

mvn -DskipTests spring-boot:build-image \
  -Ddocker.publishRegistry.username=${DOCKER_USERNAME} \
  -Ddocker.publishRegistry.password=${DOCKER_PASSWORD} \
  -Ddocker.publishRegistry.url=docker.io \
  -Dspring-boot.build-image.publish=true \
  -Dspring-boot.build-image.imageName=kacytunde/paycomputation-service:1.9
```

### Kubernetes secrets

Create the MongoDB secret before deploying (see `src/k8s/secret.yml.example`):

```bash
kubectl create secret generic paycomputation-secrets \
  --from-literal=mongodb-uri='mongodb+srv://USER:PASS@HOST/DB?tls=true&authSource=admin&replicaSet=REPLICA'
kubectl apply -f src/k8s/deployment.yml
kubectl apply -f src/k8s/service.yml
```

### Run tests

```bash
mvn test
mvn -Dtest=com.xykine.computation.ControllerIntegrationTest test
```
