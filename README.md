# Poker Server Overview

This is a sandbox environment for experimenting with Spring to implement a poker server for use as a "remote" home game.
There are surprisingly few choices out there to simulate a private home game. This project is an attempt to scratch that
itch while giving us an excuse to study WebSockets, Loom, and structured concurrency. The plan is to implement a server
first and then build (or recruit someone to build) a client using something like React or Angular?

## Dev Setup

This project is built with Java 25 and you will need to have a Java 25 JDK installed on your machine. I recommend
https://sdkman.io/ for managing your Java installations. If you are running locally, you will also need docker. This
project provides a docker-compose file for starting up a mongo server within a docker container.

## Running locally

- You need to have Java 25 installed and set as your active JDK (e.g., `sdk use java 25.0.2-tem`).
- You need to have docker installed and running on your machine.
- You need a REST client for submitting requests to the server (e.g., Postman, httpie, or curl).

### Starting the server & registering an admin user

1. Ensure docker or podman are installed and running on your machine
2. Start the Mongo database (from the root directory of the poker-server) via `docker-compose up`
3. Build and run the server (from the root directory of the server) via `./gradlew clean spring-boot:run`
4. Register an admin user using the rest endpoint `http://localhost:8080/auth/register`. The app uses a primitive
   configuration in `src/main/resources/application.yml` to denote which IDs are admins.

## REST Clients!

The server is designed to be client-agnostic. You can use any REST client to interact with the REST endpoints to
register users and manage games. The server publishes swagger documentation for the REST endpoints at
`http://localhost:8080/swagger-ui/index.html`.

## WebSocket Client interactions

The actual poker game interactions (e.g., joining a game, placing bets, etc.) are designed to be done via a
WebSocket client. The WebSocket endpoint is `ws://localhost:8080/ws`. You will need to include a valid JWT token in the
initial connection request in order to authenticate with the server and receive game updates. 

See [/docs/command-evnt-spec.md](docs/command-event-spec.md) for details on the command and event specifications for client-server interactions.
