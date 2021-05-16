# Group 34 Agententech
Members: Santiago Agurcia, Moritz Hechtbauer, Sina Heidrich, Hellen Samson

## HA1
Content of this repo is currently (09.05.21) a copy of the skeleton assignment.

### Running server and client
Server throws error: "Unable to send multicast message on interface", unless modified. 
Avoid problem by setting AotNode's parent="NodeWithDirectory" in settings.xml.

Client can't find server. Try running client on same node as server.
Change StartClient.java config to server.xml.