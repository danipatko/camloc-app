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

enum state: 0=off, 1=connected/idle

TCP commands

**client**

-   SEND config (sent on connect and manual update) **`0x0`**

    -   payload: `[msg: u8, state: u8, xpos: f32, ypos: f32, rotation: f32, fov: f32]`

-   SEND state (sent on change) **`0x2`**

    -   payload: `[msg: u8, state: u8, battery: u8, camera index: u8, xres: u16, yres: u16, focus: f32]`
    -   if the state is off (0), there are no other fields after the battery

**server**

-   SET config (sets config and state remotely) **`0x2`**

    -   payload: `[msg: u8, state: u8, camera index: u8, xpos: f32, ypos: f32, rotation: f32, fov: f32]`

-   ASK config (triggers a config send) **`0x3`**

    -   payload: `[msg: u8]`

-   SET state (sets state remotely) **`0x4`**

    -   payload: `[msg: u8, state: u8, camera index: u8, xres: u16, yres: u16, focus multiplier: f32]`

-   ASK state (triggers a state send) **`0x4`**

    -   payload: `[msg: u8]`

-   flash (sent manually, flashes phone for a few seconds) **`0x5`**
    -   payload: `[msg: u8]`

---

UDP

**client**

-   SEND X position
    -   payload `[f64]`
