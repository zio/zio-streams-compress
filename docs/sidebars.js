const sidebars = {
  sidebar: [
    "index",
    "getting-started",
    "choosing-a-codec",
    "streaming-unarchiving",
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      link: { type: "doc", id: "reference-core" },
      items: [
        {
          type: "category",
          label: "Compression Codecs",
          collapsed: false,
          items: [
            "reference-gzip",
            "reference-zstd",
            "reference-brotli",
            "reference-brotli4j",
            "reference-bzip2",
            "reference-lz4",
            "reference-snappy"
          ]
        },
        {
          type: "category",
          label: "Archive Formats",
          collapsed: false,
          items: [
            "reference-zip",
            "reference-zip4j",
            "reference-tar"
          ]
        }
      ]
    },
    "tutorial-compress-and-archive",
    {
      type: "category",
      label: "How-to Guides",
      collapsed: true,
      items: [
        "how-to-compress-and-decompress",
        "how-to-create-and-read-archives",
        "how-to-read-archives-safely",
        "how-to-read-password-protected-zip",
        "how-to-write-encrypted-zip",
        "how-to-convert-archive-formats",
        "how-to-set-entry-metadata",
        "how-to-archive-a-single-file"
      ]
    }
  ]
};

module.exports = sidebars;
