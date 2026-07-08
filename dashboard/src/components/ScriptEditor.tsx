"use client";

import dynamic from "next/dynamic";

interface ScriptEditorProps {
  value: string;
  onChange: (val: string) => void;
  readOnly?: boolean;
}

const Editor = dynamic(
  () =>
    Promise.all([
      import("@uiw/react-codemirror"),
      import("@codemirror/lang-javascript"),
      import("@codemirror/view"),
    ]).then(([codemirror, langJs, view]) => {
      const theme = view.EditorView.theme(
        {
          "&": {
            backgroundColor: "#0f1d15",
            color: "#e8f1ea",
            height: "100%",
            fontSize: "13px",
          },
          ".cm-gutters": {
            backgroundColor: "#0a140e",
            color: "#5e746a",
            border: "none",
            borderRight: "1px solid #2c4a37",
          },
          ".cm-activeLineGutter": { backgroundColor: "#16281c" },
          ".cm-activeLine": { backgroundColor: "rgba(44, 74, 55, 0.3)" },
          "&.cm-focused .cm-selectionBackground, ::selection": {
            backgroundColor: "rgba(55, 198, 212, 0.2) !important",
          },
          ".cm-cursor, .cm-dropCursor": { borderLeftColor: "#f2c14e" },
          ".cm-matchingBracket": {
            backgroundColor: "rgba(242, 193, 78, 0.15)",
            outline: "1px solid rgba(242, 193, 78, 0.3)",
          },
        },
        { dark: true }
      );

      const extensions = [langJs.javascript(), theme];

      function WrappedEditor(props: ScriptEditorProps) {
        return (
          <div className="h-full overflow-hidden rounded-lg border border-border">
            <codemirror.default
              value={props.value}
              onChange={props.onChange}
              readOnly={props.readOnly}
              extensions={extensions}
              theme="dark"
              height="100%"
              style={{ height: "100%" }}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLineGutter: true,
                highlightActiveLine: true,
                foldGutter: true,
                bracketMatching: true,
                closeBrackets: true,
                autocompletion: true,
                indentOnInput: true,
                tabSize: 4,
              }}
            />
          </div>
        );
      }

      return WrappedEditor;
    }),
  {
    ssr: false,
    loading: () => (
      <div className="flex h-full items-center justify-center bg-bg-secondary text-text-muted">
        Loading editor...
      </div>
    ),
  }
);

export default function ScriptEditor(props: ScriptEditorProps) {
  return <Editor {...props} />;
}
