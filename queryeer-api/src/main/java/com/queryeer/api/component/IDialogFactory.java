package com.queryeer.api.component;

/** Factory for creating dialogs */
public interface IDialogFactory
{
    /** Shows a modal value dialog with syntax editor with search etc. */
    void showValueDialog(String title, Object val, Format format);

    /** Format type of dialog content */
    enum Format
    {
        JSON("text/json"),
        XML("text/xml"),
        UNKOWN("text/plain");

        private final String mime;

        Format(String mime)
        {
            this.mime = mime;
        }

        public String getMime()
        {
            return mime;
        }
    }
}
