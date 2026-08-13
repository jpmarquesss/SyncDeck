using System;
using System.Threading;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    internal static class Program
    {
        [STAThread]
        private static void Main()
        {
            bool firstInstance;
            using (Mutex instance = new Mutex(false, @"Local\SyncDeck.Agent.v1", out firstInstance))
            {
                if (!firstInstance) return;
                Application.EnableVisualStyles();
                Application.SetCompatibleTextRenderingDefault(false);
                Application.Run(new AgentContext());
            }
        }
    }
}
