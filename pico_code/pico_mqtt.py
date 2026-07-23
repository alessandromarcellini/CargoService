from machine import Pin, time_pulse_us
from time import sleep
import time
import network
from umqtt.simple import MQTTClient

# ----------------- CONFIGURAZIONE -----------------
WIFI_SSID = "TIM-82409991" # wifi name
WIFI_PASSWORD = "9Gde9d7A5DDqqUQRgku6Hbp7" # wifi password

MQTT_BROKER = "192.168.1.5" # mosquitto host ip
MQTT_PORT = 1883
MQTT_CLIENT_ID = "pico_w_sonar_led"

TOPIC_LED = b"led_data"
TOPIC_SONAR = b"sonar_data"

BLINK_INTERVAL_MS = 300      # blinking interval for led
SONAR_INTERVAL_MS = 1000     # sampling rate for sonar

# ----------------- HARDWARE -----------------
led = Pin(15, Pin.OUT)
trig = Pin(2, Pin.OUT)
echo = Pin(5, Pin.IN)

# ----------------- STATE -----------------
blinking = False
last_blink_time = time.ticks_ms()
last_sonar_time = time.ticks_ms()


def connect_to_wifi():
    wlan = network.WLAN(network.STA_IF)
    wlan.active(True)
    wlan.connect(WIFI_SSID, WIFI_PASSWORD)

    print("Connecting to wifi...")
    while not wlan.isconnected():
        sleep(0.5)
        print(".", end="")

    print("\nConnected to wifi: ", wlan.ifconfig())
    return wlan


def measure_distance():
    trig.value(0)
    sleep(0.000002)
    trig.value(1)
    sleep(0.00001)
    trig.value(0)

    pulse_time = time_pulse_us(echo, 1, 30000)
    if pulse_time < 0:
        # timeout or reading error
        return None

    distance_in_cm = (pulse_time * 0.0343) / 2
    print("[SONAR] measured distance: ", str(distance_in_cm))
    return distance_in_cm


def mqtt_callback(topic, msg): # for led blinking
    global blinking

    print("Message received on topic --- ", topic, " : ", msg)

    if topic == TOPIC_LED:
        if msg == b"start_blinking":
            blinking = True
            print("[LED] Starting to blink!")
        elif msg == b"stop_blinking":
            blinking = False
            led.value(0)
            print("[LED] Stopped blinking")


def mqtt_connect():
    client = MQTTClient(MQTT_CLIENT_ID, MQTT_BROKER, port=MQTT_PORT)
    client.set_callback(mqtt_callback)
    client.connect()
    client.subscribe(TOPIC_LED)
    print("Connected to mqtt and subscribed to: ", TOPIC_LED)
    return client


def main():
    global blinking, last_blink_time, last_sonar_time

    connect_to_wifi()
    client = mqtt_connect()

    try:
        while True:
            # non-blocking check if there are messages arriving
            client.check_msg()

            now = time.ticks_ms()

            # Handling led blinking
            if blinking and time.ticks_diff(now, last_blink_time) >= BLINK_INTERVAL_MS:
                led.toggle()
                last_blink_time = now

            # Handling sonar logic
            if time.ticks_diff(now, last_sonar_time) >= SONAR_INTERVAL_MS:
                distance = measure_distance()
                last_sonar_time = now

                if distance is not None:
                    payload = str(round(distance, 1))
                    client.publish(TOPIC_SONAR, payload)
                    print("[SONAR] Published measurment: ", payload, " cm")
                else:
                    print("[SONAR] Failed measurement")

            # avoid saturating cpu
            time.sleep_ms(10)

    except Exception as e:
        print("Error:", e)
        client.disconnect()


if __name__ == "__main__":
    main()