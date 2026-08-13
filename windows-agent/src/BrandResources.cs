using System;
using System.Drawing;
using System.Windows.Forms;

namespace SyncDeck.Agent
{
    internal static class BrandResources
    {
        private static readonly Icon _applicationIcon = LoadApplicationIcon();

        public static Icon ApplicationIcon
        {
            get { return _applicationIcon; }
        }

        private static Icon LoadApplicationIcon()
        {
            try
            {
                Icon icon = Icon.ExtractAssociatedIcon(Application.ExecutablePath);
                if (icon != null) return icon;
            }
            catch (Exception)
            {
                // O ícone padrão mantém o agente funcional em builds de desenvolvimento incompletos.
            }
            return SystemIcons.Application;
        }
    }
}
