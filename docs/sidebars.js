const sidebars = {
  sidebar: [
    {
      type: "category",
      label: "ZIO Streams Compress",
      collapsed: false,
      link: { type: "doc", id: "index" },
      items: [
        {
          type: "category",
          label: "Reference",
          collapsed: false,
          link: { type: "doc", id: "reference-core" },
          items: [
            "reference-gzip",
            "reference-zstd",
            "reference-brotli",
            "reference-brotli4j",
            "reference-bzip2",
            "reference-lz4",
            "reference-snappy",
            "reference-zip",
            "reference-zip4j",
            "reference-tar"
          ]
        },
        {
          type: "category",
          label: "Tutorial",
          collapsed: false,
          link: { type: "doc", id: "tutorial-compress-and-archive" },
          items: []
        },
        {
          type: "category",
          label: "How-to Guides",
          collapsed: false,
          link: { type: "doc", id: "how-to-compress-and-decompress" },
          items: [
            "how-to-create-and-read-archives",
            "how-to-read-archives-safely",
            "how-to-read-password-protected-zip"
          ]
        }
      ]
    }
  ]
};

module.exports = sidebars;
