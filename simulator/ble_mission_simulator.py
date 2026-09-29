"""BLE GATT test device for the BLE Mission Android app.

Run on Windows 11 with a Bluetooth adapter that supports peripheral role.
The second read returns TEST OK so it cannot be mistaken for the teacher's grade.
"""

import asyncio
import uuid

from winrt.windows.devices.bluetooth import BluetoothAdapter, BluetoothError
from winrt.windows.devices.bluetooth.genericattributeprofile import (
    GattCharacteristicProperties,
    GattLocalCharacteristicParameters,
    GattProtectionLevel,
    GattServiceProvider,
    GattServiceProviderAdvertisingParameters,
    GattServiceProviderAdvertisementStatus,
    GattWriteOption,
)
from winrt.windows.storage.streams import DataWriter


SERVICE_UUID = uuid.UUID("aee04821-1973-4e1f-a590-e84b10d580e7")
CHAR_UUID = uuid.UUID("cde07b1a-889b-44b7-a99f-c888dddac729")


def buffer_from_bytes(data: bytes):
    writer = DataWriter()
    try:
        writer.write_bytes(data)
        return writer.detach_buffer()
    finally:
        writer.close()


async def main():
    adapter = await BluetoothAdapter.get_default_async()
    if adapter is None:
        raise RuntimeError("No Bluetooth adapter found. Turn on Bluetooth on the laptop.")
    if not adapter.is_peripheral_role_supported:
        raise RuntimeError("This Bluetooth adapter cannot act as a BLE peripheral.")

    provider_result = await GattServiceProvider.create_async(SERVICE_UUID)
    if provider_result.error != BluetoothError.SUCCESS:
        raise RuntimeError(f"Cannot create BLE service: {provider_result.error}")
    provider = provider_result.service_provider

    parameters = GattLocalCharacteristicParameters()
    parameters.characteristic_properties = (
        GattCharacteristicProperties.READ | GattCharacteristicProperties.WRITE
    )
    parameters.read_protection_level = GattProtectionLevel.PLAIN
    parameters.write_protection_level = GattProtectionLevel.PLAIN
    parameters.user_description = "BLE Mission test value"
    characteristic_result = await provider.service.create_characteristic_async(
        CHAR_UUID, parameters
    )
    if characteristic_result.error != BluetoothError.SUCCESS:
        raise RuntimeError(f"Cannot create BLE characteristic: {characteristic_result.error}")
    characteristic = characteristic_result.characteristic

    loop = asyncio.get_running_loop()
    state = {"value": b"68", "names": None}

    async def serve_read(args, deferral):
        try:
            request = await args.get_request_async()
            if request is None:
                print("Read request expired", flush=True)
                return
            request.respond_with_value(buffer_from_bytes(state["value"]))
            print(f"READ  -> {state['value'].decode('utf-8')}", flush=True)
        except Exception as error:
            print(f"READ ERROR: {error}", flush=True)
        finally:
            deferral.complete()

    async def serve_write(args, deferral):
        try:
            request = await args.get_request_async()
            if request is None:
                print("Write request expired", flush=True)
                return
            raw = bytes(request.value)
            names = raw.decode("utf-8", errors="replace")
            state["names"] = names
            state["value"] = b"TEST OK"
            if request.option == GattWriteOption.WRITE_WITH_RESPONSE:
                request.respond()
            print(f"WRITE <- {names!r}", flush=True)
            print("Next read will return TEST OK", flush=True)
        except Exception as error:
            print(f"WRITE ERROR: {error}", flush=True)
        finally:
            deferral.complete()

    def on_read(sender, args):
        deferral = args.get_deferral()
        asyncio.run_coroutine_threadsafe(serve_read(args, deferral), loop)

    def on_write(sender, args):
        deferral = args.get_deferral()
        asyncio.run_coroutine_threadsafe(serve_write(args, deferral), loop)

    def on_status(sender, args):
        print(f"Advertisement: {args.status} (error: {args.error})", flush=True)

    read_token = characteristic.add_read_requested(on_read)
    write_token = characteristic.add_write_requested(on_write)
    status_token = provider.add_advertisement_status_changed(on_status)
    advertising = GattServiceProviderAdvertisingParameters()
    advertising.is_connectable = True
    advertising.is_discoverable = True

    try:
        provider.start_advertising_with_parameters(advertising)
        await asyncio.sleep(1)
        status = provider.advertisement_status
        if status not in (
            GattServiceProviderAdvertisementStatus.STARTED,
            GattServiceProviderAdvertisementStatus.STARTED_WITHOUT_ALL_ADVERTISEMENT_DATA,
        ):
            raise RuntimeError(f"BLE advertising did not start: {status}")
        print("BLE Mission test device is running.", flush=True)
        print(f"Service: {SERVICE_UUID}", flush=True)
        print(f"Characteristic: {CHAR_UUID}", flush=True)
        print("Phone: scan, connect, read 68, send two names, read TEST OK.", flush=True)
        print("Press Ctrl+C to stop.", flush=True)
        await asyncio.Event().wait()
    finally:
        provider.stop_advertising()
        characteristic.remove_read_requested(read_token)
        characteristic.remove_write_requested(write_token)
        provider.remove_advertisement_status_changed(status_token)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("Stopped.")
    except Exception as exc:
        print(f"ERROR: {exc}")
        raise SystemExit(1)
