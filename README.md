![shid](https://img.shields.io/badge/camed✅-loced✅-82aaff)

le broker

```shell
docker run -it --name broker -p 1883:1883 -p 9001:9001 -v .\\mosquitto.conf:/mosquitto/config/mosquitto.conf eclipse-mosquitto
```

le mDNS publisher

```shell
avahi-publish -s _camloc _mqtt._tcp 1883 "app=camloc"

# if the broker is on another machine:

avahi-publish -s _camloc _mqtt._tcp 1883 "app=camloc" -H <broker-ip>.local
```

---

Network protocol rework

-   clients are searching for a `_camloc._tcp` service published by mDNS
-   when detected, try connection on tcp for config exchange and other utils (udp is for the x position stream)

---

SERVER -> CLIENT

ASK CONFIG (all)

SET CONFIG (x, y, rot)

SET STATE (tracking on/off)

SET FLASH

SET CAMERA (camera: 0|1|2, resolution: 0|1|2|3|4, focus)

---

CLIENT -> SERVER

SET CONFIG (ALL)

SET CONFIG (x, y, rot)

SET STATE AND CAMERA (tracking, camera: 0|1|2, fov, resolution: 0|1|2|3|4, focus, battery)
