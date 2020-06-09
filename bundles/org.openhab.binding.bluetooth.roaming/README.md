# Bluetooth Roaming Adapter

This extension adds support for accessing Bluetooth devices from any other configured adapter via a virtual adapter.

## Supported Things

It defines the following bridge type:

| Bridge Type ID | Description                                                                                                |
|----------------|------------------------------------------------------------------------------------------------------------|
| roaming        | A virtual Bluetooth adapter that interacts with Bluetooth devices through their nearest Bluetooth adapter. |

## Channels

Devices which use a `roaming` adapter as their bridge also gain the following channels:

| Channel ID       | Item Type | Description                                                     |
|------------------|-----------|-----------------------------------------------------------------|
| adapter-uid      | String    | The thingUID of the adapter that is nearest to this device      |
| adapter-location | String    | The value of the `Location` specified for the nearest adapter    |

## Discovery

A default roaming adapter is discovered automatically, although you can still configure it textually if you want.
There can be only a single roaming adapter on a system.

## Bridge Configuration

The roaming bridge requires an `address` parameter which mearly servers as an identifier for the virtual adapter, its value doesn't really matter. 
It is advised to make sure it is set to a value outside of the normal bluetooth mac address range, for example: "FF:FF:FF:FF:FF:FF".

Additionally, the parameter `discovery` can be set to `true` or `false`. 
When set to `true`, a device discovered on any adapter will have a corresponding `roaming` discovery.

## Example

This is how an Roaming adapter can be configured textually in a *.things file:

```
Bridge bluetooth:roaming:ctrl "BLE Roaming Adapter" [ address="FF:FF:FF:FF:FF:FF", discovery=true ]
```
