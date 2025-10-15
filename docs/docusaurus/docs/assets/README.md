# How to include images in the docs

Note: There is a difference between the imports in `.mdx` files and raw `.md` files, as `.mdx` files support React components. 

## .mdx files
### PNG
1. Import the `.tsx` component to render the image
   ```
   import Image from "../src/components/image/image";
   ```
2. Import the image
   ```
   import SeoMetaHeaderExample from './assets/seo-meta-header-example.png';
   ```
3. Use the imported image in the `Image` component
   ```
    <Image src={SeoMetaHeaderExample} alt={"Example of a good heading structure shown in the SEO META in 1 CLICK tool"} />
    ```
   
### SVGs
1. Import the SVG as ReactComponent
   ```
   import RedirectIcon from './assets/arrow-up-right-from-square-solid-full.svg';
   ```
2. Use the imported SVG as a component
   ```
   <RedirectIcon style={{width: "20px", height: "20px"}} alt={"Redirect Icon"} aria-label={"Redirect Icon"} />
   ```


## raw .md files

While displaying a picture in a raw .md file is quite simple, you cannot style the image properly *(at least I did not find a good way to do so)*.
Also, most files will most likely be `.mdx` files anyway.

This display option also works for `.mdx` files, but using the `<Image />` component ensures consistency and should be used preferably.

```
![SEO META in 1 CLICK](./assets/seo-meta-header-example.png)
```