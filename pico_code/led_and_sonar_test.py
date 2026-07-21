from machine import Pin, time_pulse_us
from time import sleep

# LED esterno su GP15
led = Pin(15, Pin.OUT)

# Sonar HC-SR04
trig = Pin(2, Pin.OUT)
echo = Pin(5, Pin.IN)


def misura_distanza():
    # manda impulso TRIG
    trig.value(0)
    sleep(0.000002)

    trig.value(1)
    sleep(0.00001)

    trig.value(0)

    # misura durata impulso ECHO
    durata = time_pulse_us(echo, 1, 30000)

    # conversione in centimetri
    distanza = (durata * 0.0343) / 2

    return distanza


while True:
    # misura distanza
    distanza = misura_distanza()

    print("Distanza:", round(distanza, 1), "cm")

    # lampeggio LED
    led.toggle()

    # attesa 0.5 secondi
    sleep(0.5)