# Guide for Developers
If you are new to Jekyll and plan on either adding documentation/news, or updating the code, you should check out the tutorials on their [website](https://jekyllrb.com/).

## Adding News Items
News items exist as posts, which are stored in `_posts`. The filename of a post must be of the following format `yyyy-mm-dd-title.markdown`.

To add a new news item, do the following:
1. Create a file in `_posts` using the filename format mentioned above.
2. Add to the front matter the layout, title, date and categories.

  layout: post

  title:  "First Workflow from the PCAWG Project released"

  date:   2015-11-18 09:00:00 -0400

  categories: dockstore
3. If your post has images, place them in `assets/images/news`

## Adding Documentation
Documentation is currently split up into three sections: prerequisites, user tutorials, and developer tutorials. The organization of the documentation can be seen in `_data/docs.yml`. The actual documents are stored in `_docs`.

To add a new document to an existing section, do the following:
1. Create a file in the folder `_docs`
2. In the front matter, put the title and permalink. Below is an example:
```
  title: Advanced Features

  permalink: /docs/publisher-tutorials/advanced-features
```
3. Add to the appropriate list in `_data/docs.yml` the filename (not including extension)
4. If the doc has images, place them in `assets/images/docs`
