# VV OTX Microservice
A RESTful microservice with a searchable frontend, built in Clojure, Pedestal, Component, and ClojureScript to serve and search AlienVault OTX pulses.

## Setup
- Install Leiningen: https://leiningen.org/
- Run: `lein run`
- Build UberJar: `./build.sh`
- Build Docker: `DOCKER_BUILDKIT=1 docker build --no-cache -t vv-otx-service .`
- Run Docker: `docker run -p 8080:8080 vv-otx-service`
- Navigate to frontend: `http://localhost:8080`

- ## Endpoints
- `/`: ClojureScript frontend for easy searching.
- `GET /indicators/:id`: Get an indicator by ID.
- `GET /indicators`: Get all indicators.
- `GET /indicators?type=:type`: Get indicators by type.
- `POST /indicators/search`: Search indicators with JSON criteria


For example:

- curl -X GET 'http://localhost:8080/indicators?type=IPv4'
- curl -X GET 'http://localhost:8080/indicators'
- curl -X GET http://localhost:8080/indicators/460576
- curl -v -H "Content-Type: application/json" -d '{"created":"2018-07-09T18:02:40"}' http://localhost:8080/indicators/search


- ## Tests
- Run tests: `lein test`

- ## References
- Pedestal Documentation: http://pedestal.io/
- Clojurians Pedestal Chat to learn that version 8.0.0-beta-1 supports #{set} routes that match on method and path
- Component Library: https://github.com/stuartsierra/component
- Clojure JSON: https://github.com/clojure/data.json
- Dockerizing Clojure: https://medium.com/@gitlab/continuous-delivery-setup-for-clojure-java-with-docker-596f7a135c4c[](https://medium.com/%40mprokopov/gitlab-continuous-delivery-setup-for-clojure-java-with-docker-472320d5aa52)
