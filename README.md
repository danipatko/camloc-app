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
