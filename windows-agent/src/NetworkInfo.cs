using System;
using System.Linq;
using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;

namespace SyncDeck.Agent
{
    internal static class NetworkInfo
    {
        public static string[] LocalIPv4Addresses()
        {
            return NetworkInterface.GetAllNetworkInterfaces()
                .Where(x => x.OperationalStatus == OperationalStatus.Up && x.NetworkInterfaceType != NetworkInterfaceType.Loopback)
                .SelectMany(x => x.GetIPProperties().UnicastAddresses)
                .Where(x => x.Address.AddressFamily == AddressFamily.InterNetwork && IsPrivateOrLoopback(x.Address))
                .Select(x => x.Address.ToString())
                .Distinct().ToArray();
        }

        public static WakeConfiguration GetWakeConfiguration(IPAddress localAddress)
        {
            if (localAddress == null || localAddress.AddressFamily != AddressFamily.InterNetwork ||
                !IsPrivateOrLoopback(localAddress)) return null;

            foreach (NetworkInterface adapter in NetworkInterface.GetAllNetworkInterfaces())
            {
                try
                {
                    if (adapter.OperationalStatus != OperationalStatus.Up ||
                        adapter.NetworkInterfaceType == NetworkInterfaceType.Loopback) continue;
                    byte[] mac = adapter.GetPhysicalAddress().GetAddressBytes();
                    if (mac == null || mac.Length != 6) continue;

                    foreach (UnicastIPAddressInformation address in adapter.GetIPProperties().UnicastAddresses)
                    {
                        if (address.Address.AddressFamily != AddressFamily.InterNetwork ||
                            !address.Address.Equals(localAddress)) continue;
                        IPAddress broadcast = BroadcastAddress(address.Address, address.IPv4Mask);
                        if (broadcast == null) return null;
                        return new WakeConfiguration
                        {
                            MacAddress = BitConverter.ToString(mac),
                            BroadcastAddress = broadcast.ToString(),
                            Port = 9,
                            InterfaceName = string.IsNullOrWhiteSpace(adapter.Description)
                                ? adapter.Name : adapter.Description
                        };
                    }
                }
                catch { }
            }
            return null;
        }

        private static IPAddress BroadcastAddress(IPAddress address, IPAddress mask)
        {
            if (address == null || mask == null) return null;
            byte[] ip = address.GetAddressBytes();
            byte[] subnet = mask.GetAddressBytes();
            if (ip.Length != 4 || subnet.Length != 4) return null;
            byte[] broadcast = new byte[4];
            for (int i = 0; i < 4; i++) broadcast[i] = (byte)(ip[i] | (subnet[i] ^ 255));
            return new IPAddress(broadcast);
        }

        public static bool IsPrivateOrLoopback(IPAddress address)
        {
            if (IPAddress.IsLoopback(address)) return true;
            byte[] bytes = address.GetAddressBytes();
            if (bytes.Length != 4) return false;
            return bytes[0] == 10 ||
                   (bytes[0] == 172 && bytes[1] >= 16 && bytes[1] <= 31) ||
                   (bytes[0] == 192 && bytes[1] == 168) ||
                   (bytes[0] == 169 && bytes[1] == 254);
        }
    }
}
