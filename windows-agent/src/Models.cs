using System;
using System.Collections.Generic;

namespace SyncDeck.Agent
{
    public sealed class ActionDefinition
    {
        public string Id { get; set; }
        public string Label { get; set; }
        public string Type { get; set; }
        public string Target { get; set; }
        public string Arguments { get; set; }
        public string WorkingDirectory { get; set; }
        public string[] ProcessNames { get; set; }
        public string[] AppNames { get; set; }
        public string FallbackUrl { get; set; }
        public string Icon { get; set; }
        public string Color { get; set; }
        public bool Confirm { get; set; }
        public bool Closable { get; set; }
        public bool Enabled { get; set; }

        public ActionDefinition Clone()
        {
            return new ActionDefinition
            {
                Id = Id,
                Label = Label,
                Type = Type,
                Target = Target,
                Arguments = Arguments,
                WorkingDirectory = WorkingDirectory,
                ProcessNames = ProcessNames == null ? new string[0] : (string[])ProcessNames.Clone(),
                AppNames = AppNames == null ? new string[0] : (string[])AppNames.Clone(),
                FallbackUrl = FallbackUrl,
                Icon = Icon,
                Color = Color,
                Confirm = Confirm,
                Closable = Closable,
                Enabled = Enabled
            };
        }
    }

    public sealed class PublicAction
    {
        public string Id { get; set; }
        public string Label { get; set; }
        public string Type { get; set; }
        public string Icon { get; set; }
        public string ImageKey { get; set; }
        public string Color { get; set; }
        public bool Confirm { get; set; }
        public bool Closable { get; set; }
        public bool IsOpen { get; set; }
        public int WindowCount { get; set; }
    }

    public sealed class ActionState
    {
        public string Id { get; set; }
        public bool IsOpen { get; set; }
        public int WindowCount { get; set; }
    }

    public sealed class WakeConfiguration
    {
        public string MacAddress { get; set; }
        public string BroadcastAddress { get; set; }
        public int Port { get; set; }
        public string InterfaceName { get; set; }
    }

    public sealed class ClientRecord
    {
        public string ClientId { get; set; }
        public string DeviceName { get; set; }
        public string ProtectedSecret { get; set; }
        public string PairedAtUtc { get; set; }
    }

    public sealed class PairRequest
    {
        public string Payload { get; set; }
    }

    public sealed class PairPayload
    {
        public string Code { get; set; }
        public string ClientId { get; set; }
        public string DeviceName { get; set; }
        public string Secret { get; set; }
    }

    public sealed class ExecuteRequest
    {
        public string ActionId { get; set; }
        public string Operation { get; set; }
        public bool Confirmed { get; set; }
    }

    public sealed class SaveActionRequest
    {
        public ActionDefinition Action { get; set; }
        public string SelectionToken { get; set; }
    }

    public sealed class CatalogApplication
    {
        public string Name { get; set; }
        public string Target { get; set; }
        public string[] ProcessNames { get; set; }
        public string[] AppNames { get; set; }
        public string Icon { get; set; }
        public string Color { get; set; }
        public string SelectionToken { get; set; }
    }

    public sealed class PickPathRequest
    {
        public string Kind { get; set; }
    }

    public sealed class PickedPath
    {
        public string Label { get; set; }
        public string Type { get; set; }
        public string Target { get; set; }
        public string[] ProcessNames { get; set; }
        public string[] AppNames { get; set; }
        public string Icon { get; set; }
        public string Color { get; set; }
        public string SelectionToken { get; set; }
    }

    public sealed class DeleteActionRequest
    {
        public string ActionId { get; set; }
    }

    public sealed class ApiEnvelope
    {
        public bool Ok { get; set; }
        public object Data { get; set; }
        public string Message { get; set; }
        public string Code { get; set; }
    }

    public sealed class ExecutionResult
    {
        public bool Ok { get; set; }
        public string Message { get; set; }

        public static ExecutionResult Success(string message)
        {
            return new ExecutionResult { Ok = true, Message = message };
        }

        public static ExecutionResult Failure(string message)
        {
            return new ExecutionResult { Ok = false, Message = message };
        }
    }

    public sealed class AgentSettings
    {
        public int Port { get; set; }
        public bool StartupConfigured { get; set; }
    }

    public sealed class HttpRequestData
    {
        public string Method { get; set; }
        public string Path { get; set; }
        public Dictionary<string, string> Headers { get; set; }
        public byte[] BodyBytes { get; set; }

        public string BodyText
        {
            get { return System.Text.Encoding.UTF8.GetString(BodyBytes ?? new byte[0]); }
        }
    }

    public sealed class AuthContext
    {
        public byte[] Secret { get; set; }
        public string Nonce { get; set; }
        public string ClientId { get; set; }
        public string DeviceName { get; set; }
        public string RemoteAddress { get; set; }
        public bool Encrypted { get; set; }
    }
}
