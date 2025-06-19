//Configuramos el servidor: Definimos el servidor al que el dispositivo se conectará en thinger.io
#define THINGER_SERVER "acme.thinger.io"
#define HEXACORE_DEVELOPMENT
#define THINGER_SERIAL_DEBUG
#define _DISABLE_TLS_

// Si no definimos USE_WIFI, se usa NB-IoT
//#define USE_WIFI 

//Configuración de comunicación serie
// Set serial for debug console (to the Serial Monitor, default speed 115200)
#define SerialMon Serial
#define SerialAT Serial1

//Definimos el modem si no estamos usando WIFI
#ifndef USE_WIFI  
  #define TINY_GSM_MODEM_BC660
  #define TINY_GSM_DEBUG SerialMon //Seleccionamos el monitor serie para depuración
  #ifndef TINY_GSM_RX_BUFFER
    #define TINY_GSM_RX_BUFFER 1024 //Se establece el buffer de recepción de 1024 bytes
  #endif
#endif

//Configuración del modo de bajo consumo: El ESP32 se pondrá en modo suspensión por 10 segundos para ahorrar energía
// Hibernación
#define uS_TO_S_FACTOR 1000000ULL  // Conversión de microsegundos a segundos
#define TIME_TO_SLEEP  10           // Time ESP32 will go to sleep (in seconds) 

//Incluimos las librerías necesarias
#include <Arduino.h>
#include <EEPROM.h> //Para almacenamiento no volátil
// Librerías para NB-IoT y Thinger.io
#ifndef USE_WIFI
  #include <TinyGsmClient.h> //Para comunicación con el módem
  #include <ThingerTinyGSM.h> //Para integración con thinger.io
#else
  #include <ThingerESP32.h>
#endif

#include <ThingerESP32OTA.h> //Para actualizaciones OTA
#include "arduino_secrets.h" //Archivo con las credenciales del dispositivo que vamos a conectar a thinger.io

//Incluimos las librerías del sensor BME680
#include "bsec.h"
#include <Wire.h>

//Definimos la función que se usará para comprobar el estado de los sensores
void checkIaqSensorStatus(void);
void errLeds(void);
String output;

unsigned long lastSendTime = 0; // Variable para almacenar el tiempo de la última lectura
const unsigned long sendInterval = 3000; // Intervalo de envío en milisegundos (60 segundos)

//Configuramos el sistema
// Declaración de thing según conectividad. Para NB-IoT se usa ThingerTinyGSM y para WiFi ThingerESP32
#ifdef USE_WIFI
  ThingerESP32 thing(USERNAME, DEVICE_ID, DEVICE_CREDENTIAL);
#else
  #ifdef DUMP_AT_COMMANDS
    #include <StreamDebugger.h>
    StreamDebugger debugger(SerialAT, SerialMon);
    ThingerTinyGSM thing(USERNAME, DEVICE_ID, DEVICE_CREDENTIAL, debugger);
  #else
    //Creamos un objeto thing para manejar la comunicación con thinger.io
    ThingerTinyGSM thing(USERNAME, DEVICE_ID, DEVICE_CREDENTIAL, SerialAT);
  #endif
#endif

//Creamos un objeto ota para habilitar las actualizaciones remotas
// Inicialización de OTA 
ThingerESP32OTA ota(thing);

//Creamos un string para guardar el iccid
String iccid;
String id2 = "BC660_";

//Configuración de la versión del módulo
#define MODULE_CONFIG_VERSION 3

//Configuración de pines
#ifdef HEXACORE_DEVELOPMENT
  #define PIN_MODEM_RESET 13
  #define PIN_MODEM_WAKEUP 12
  #define PIN_MODEM_PWR_KEY 19
  #define PIN_MODULE_LED 21
  #define PIN_MODEM_RX 32
  #define PIN_MODEM_TX 33
  #define RELAY_PIN 27
  #define DOOR_STATE_PIN 14
  // M.2 pins
#else
  #define PIN_MODEM_RESET 5
  #define PIN_MODEM_WAKEUP 18
  #define PIN_MODEM_PWR_KEY 23
  #define PIN_MODULE_LED 21
  #define PIN_MODEM_RX 4
  #define PIN_MODEM_TX 13
  #define DONE_PIN 19
#endif

//Definimos los pines del sensor (Si tienen direcciones distintas se puede usar el mismo I2C bus)
#define I2C_SDA 5  // SDA en Thinger32 NB-IoT
#define I2C_SCL 4  // SCL en Thinger32 NB-IoT

//#define CALIBRATE_PRESSURE  1013.25

//Definimos el sensor BME680
Bsec iaqSensor;
String timestamp;
float measurements[6]={0,0,0,0,0,0}; //temp, hum, pressure, aiq, co2, voc

// Función de inicialización de sensores. 
void sensors_begin(){
  Serial.println("Inicializando sensores...");
 
  float sea_level;

  while(!Serial);
  delay(1000);
  Serial.println();
  //Configuramos el bus I2C en los pines correctos
  Wire.begin(I2C_SDA, I2C_SCL);
  iaqSensor.begin(0x77, Wire);
  output = "\nBSEC library version " + String(iaqSensor.version.major) + "." + String(iaqSensor.version.minor) + "." + String(iaqSensor.version.major_bugfix) + "." + String(iaqSensor.version.minor_bugfix);
  Serial.println(output);
  checkIaqSensorStatus();

  bsec_virtual_sensor_t sensorList[13] = {
    BSEC_OUTPUT_IAQ,
    BSEC_OUTPUT_STATIC_IAQ,
    BSEC_OUTPUT_CO2_EQUIVALENT,
    BSEC_OUTPUT_BREATH_VOC_EQUIVALENT,
    BSEC_OUTPUT_RAW_TEMPERATURE,
    BSEC_OUTPUT_RAW_PRESSURE,
    BSEC_OUTPUT_RAW_HUMIDITY,
    BSEC_OUTPUT_RAW_GAS,
    BSEC_OUTPUT_STABILIZATION_STATUS,
    BSEC_OUTPUT_RUN_IN_STATUS,
    BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_TEMPERATURE,
    BSEC_OUTPUT_SENSOR_HEAT_COMPENSATED_HUMIDITY,
    BSEC_OUTPUT_GAS_PERCENTAGE
  };

  iaqSensor.updateSubscription(sensorList, 13, BSEC_SAMPLE_RATE_LP);
  checkIaqSensorStatus();
  
  Serial.println("Sensor BME680 inicializado correctamente!");

}

//Función para detectar el timestamp actual
String getFormattedTimestamp(){
  //Obtenemos la hora del módem desde NTP
  if(thing.getTinyGsm().NTPServerSync("pool.ntp.org", 0)){
    delay(2000);

    //Enviar comando AT para leer la hora del módem
    thing.getTinyGsm().sendAT("+CCLK?");
    if(thing.getTinyGsm().waitResponse(1000, "+CCLK:") == 1){
      String response = thing.getTinyGsm().stream.readStringUntil('\n');

      //Extraemos la fecha y hora de la respuesta
      response.trim();
      int start = response.indexOf("\"") + 1;
      int end = response.lastIndexOf("\"");
      String dateTime = response.substring(start, end);

      //Convertir a formato ISO 8601
      int year = 2000 + dateTime.substring(0, 2).toInt();
      int month = dateTime.substring(3, 5).toInt();
      int day = dateTime.substring(6, 8).toInt();
      int hour = dateTime.substring(9, 11).toInt();
      int minute = dateTime.substring(12, 14).toInt();
      int second = dateTime.substring(15, 17).toInt();

      char iso_time[25];
      sprintf(iso_time, "%04d-%02d-%02dT%02d:%02d:%02d", year, month, day, hour, minute, second);
      return String(iso_time);
    }
    else{
      Serial.println("Error al leer la hora del módem");
    }
  }
  else{
    Serial.println("Error al sincronizar con el servidor NTP");
  }
  return "NTP_ERROR";
}

void setup() {
  // Configuración común
  pinMode(PIN_MODULE_LED, OUTPUT);
  Serial.begin(115200);

  //Configuramos el WiFi o el NB-IoT dependiendo de lo que esté definido
  #ifdef USE_WIFI
    Serial.println("Conectando a WiFi...");
    thing.add_wifi(WIFI_SSID, WIFI_PASSWORD);
  #else
    Serial1.begin(115200, SERIAL_8N1, PIN_MODEM_RX, PIN_MODEM_TX);
    Serial.println("Conectando a NB-IoT...");
    thing.setAPN(APN_NAME, APN_USER, APN_PSWD);
  #endif
  
  //Inicializamos el sensor BME680
  sensors_begin();

  // Datos que se envían a Thinger.io
  //Creamos el recurso de salida data en thinger.io
  thing["data"] >> [](pson & out){
    // Leer datos del BME680
    out["timestamp"] = timestamp;
    out["temperature"] = measurements[0];
    out["humidity"] = measurements[1];
    out["pressure"] = measurements[2];
    out["iaq"] = measurements[3];
    out["co2"] = measurements[4];
    out["voc"] = measurements[5];
  };

  // Si se usa NB-IoT, se configuran recursos y parámetros del módem
  #ifndef USE_WIFI
   thing["modem"] >> [](pson & out){
      out["modem"] =    thing.getTinyGsm().getModemInfo().c_str();
      out["IMEI"] =     thing.getTinyGsm().getIMEI().c_str();
      out["CCID"] =     thing.getTinyGsm().getSimCCID().c_str();
      out["operator"] = thing.getTinyGsm().getOperator().c_str();
    };

    // Configura APN para NB-IoT
    thing.setAPN(APN_NAME, APN_USER, APN_PSWD);

    // Configura el reset del módulo
    thing.setModuleReset([]{
      digitalWrite(PIN_MODEM_RESET, HIGH);
      delay(100);
      digitalWrite(PIN_MODEM_RESET, LOW);
    });

    thing.initModem([](TinyGsm& modem){
      // read SIM ICCID
      iccid = modem.getSimCCID();
      THINGER_DEBUG_VALUE("NB-IOT", "SIM ICCID: ", iccid.c_str());

      // disable power save mode
      modem.sendAT("+CPSMS=0");
      modem.waitResponse();

      // disable eDRX
      modem.sendAT("+CEDRXS=0");
      modem.waitResponse();

      // edRX and PTW -> disabled
      modem.sendAT("+QEDRXCFG=0");
      modem.waitResponse();

      // initialize module configuration for the first time
      EEPROM.begin(1);
      auto state = EEPROM.readByte(0);
      if(state!=MODULE_CONFIG_VERSION){
         THINGER_DEBUG_VALUE("NB-IOT", "Configuring module with version: ", MODULE_CONFIG_VERSION);

         // stop modem functionality
         modem.sendAT("+CFUN=0");
         modem.waitResponse();

         // configure APN
         modem.sendAT("+QCGDEFCONT=\"IP\",\"" APN_NAME "\"");
         modem.waitResponse();

         // preferred search bands (for Spain)
         modem.sendAT("+QBAND=3,20,8,3");
         modem.waitResponse();

         // set preferred operators
         modem.sendAT("+COPS=4,2,\"21407\"");  // movistar
         //modem.sendAT("+COPS=4,2,\"21401\"");  // vodafone
         modem.waitResponse();

         // enable net led
         modem.sendAT("+QLEDMODE=1");
         modem.waitResponse();

         // full functionality
         modem.sendAT("+CFUN=1");
         modem.waitResponse();

         EEPROM.writeByte(0, MODULE_CONFIG_VERSION);
         EEPROM.commit();
      }else{
         THINGER_DEBUG_VALUE("NB-IOT", "Module already configured with version: ", MODULE_CONFIG_VERSION);
      }
      EEPROM.end();
   });
  
  #endif

  // Configura OTA (común para ambos)
  ota.set_block_size(512);

 #ifdef USE_WIFI
   thing.set_credentials(USERNAME, DEVICE_ID, DEVICE_CREDENTIAL);
 #else
   id2 += iccid;  // Genera el DEVICE_ID dinámicamente solo para NB-IoT, pero podemos usar las credenciales definidas
   thing.set_credentials(USERNAME, DEVICE_ID, DEVICE_CREDENTIAL);
 #endif
}

void loop() {
  unsigned long currentMillis = millis();

  //Se ejecuta cada 60 segundos
  if(currentMillis - lastSendTime >= sendInterval){
    lastSendTime = currentMillis;
  
    if(iaqSensor.run()){
      Serial.print("IAQ Accuracy: ");
      Serial.println(iaqSensor.iaqAccuracy);

      if(iaqSensor.iaqAccuracy){
        // Leer datos del BME680
        Serial.println("Leyendo datos del sensor");
        timestamp = getFormattedTimestamp();
        Serial.println("Timestamp actual: " + timestamp);
        measurements[0] = iaqSensor.temperature;
        measurements[1] = iaqSensor.humidity;
        measurements[2] = iaqSensor.pressure / 100.0; // En Pa
        measurements[3] = iaqSensor.staticIaq;
        measurements[4] = iaqSensor.co2Equivalent;
        measurements[5] = iaqSensor.breathVocEquivalent;

        //Enviar los datos al endpoint:
        thing.call_endpoint("Endpoint_BME680", thing["data"]);
        thing.call_endpoint("Endpoint_prueba", thing["data"]);
        Serial.println("Datos enviados a los endpoints");
      }
    }
    else{
      checkIaqSensorStatus();
    }
  }
  thing.handle(); //stream data to the platform bucket

}

void checkIaqSensorStatus(void)
{
  if (iaqSensor.bsecStatus != BSEC_OK) {
    if (iaqSensor.bsecStatus < BSEC_OK) {
      output = "BSEC error code : " + String(iaqSensor.bsecStatus);
      Serial.println(output);
      for (;;)
        errLeds(); /* Halt in case of failure */
    } else {
      output = "BSEC warning code : " + String(iaqSensor.bsecStatus);
      Serial.println(output);
    }
  }

  if (iaqSensor.bme68xStatus != BME68X_OK) {
    if (iaqSensor.bme68xStatus < BME68X_OK) {
      output = "BME68X error code : " + String(iaqSensor.bme68xStatus);
      Serial.println(output);
      for (;;)
        errLeds(); /* Halt in case of failure */
    } else {
      output = "BME68X warning code : " + String(iaqSensor.bme68xStatus);
      Serial.println(output);
    }
  }
}

void errLeds(void)
{
  pinMode(PIN_MODULE_LED, OUTPUT);
  digitalWrite(PIN_MODULE_LED, HIGH);
  delay(100);
  digitalWrite(PIN_MODULE_LED, LOW);
  delay(100);
}