# Air Quality Monitoring
This project consists of an enviromental monitoring system designed for industrial enviroments using a low-cost IoT device based on the **Thinger32 NB-IoT** board and the **BME680** sensor. The collected data is transmitted via **NB-IoT** to the **Thinger.io** platform, stored in **Redis Cloud**, and evaluated using real-time meteorological data through a **Vert.x** microservice deployed on **Google Cloud Run**. Based on the evaluation, the systema automatically sends alerts through **Telegram**.

![image](https://github.com/user-attachments/assets/7267047d-e29c-47d1-8bd9-5530191ebfdf)

## Overview
This project combines IoT technologies, cloud-native microservices, and meteorological data to provide a scalable and accesible **air quality monitoring solution**. The system is designed to operate in areas without Wi-Fi infraestructure, using only NB-IoT connectivity. In addition, it allows for automatic alerting via Telegram when potentially hazardous air quality conditions are detected.
## Objectives
- Measure enviromental variables such as temperature, humidity, pressure, IAQ, CO₂, equivalent and VOCs using the BME680 sensor.
- Transmit real-time data over NB-IoT to Thinger.io
- Store measurements in Redis Cloud via a Vert.x-based microservice
- Evaluate air quality by combining sensor data with weather observations retrieved from the **AEMET API**
- Send alert messages via Telegram when thresholds are exceeded.

## Hardware used
### Thinger32 NB-IoT Board
ESP32 + Quectel BC660K-GL: https://docs.thinger.io/others/hardware/thinger32-nb-iot
### BME680 sensor
Environmental sensor for temperature, humidity, air quality, and atmospheric pressure
## Project Structure
- `auxiliar_microservice`: Auxiliary microservice for testing. Receives data from Thinger.io to verify that POST requests are correctly transmitted
- `codigo_sensor`: Code for the **Thinger32 NB-IoT**, which reads data from the BME680 sensor and sends it to **Thinger.io** using NB-IoT and the `TinyGSM` library.
- `microservice_airquality`: Microservice developed in **Java with Vertx.x** to store and retrieve air quality data from **Redis Cloud**
- `microservice_airquality_evaluation`: Microservice that evaluates air quality by combining Redis data with meteorological observations from the **AEMET API**
- `README.md`

## Author
Malena Roldán Astorga - 2025 - Master's Thesis Project
