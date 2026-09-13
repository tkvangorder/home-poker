import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, of, tap } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class SvgLoaderService {
  private httpClient = inject(HttpClient);

  private cache: { [key: string]: SVGElement } = {};

  loadSvg(url: string): Observable<SVGElement> {
    if (this.cache[url]) {
      return of(this.cache[url].cloneNode(true) as SVGElement);
    }

    return this.httpClient.get(url, { responseType: 'text' }).pipe(
      map((svgText) => {
        const svgElement = document.createElement('div');
        svgElement.innerHTML = svgText;
        const svg = svgElement.querySelector('svg');
        if (!svg) {
          throw new Error(`No SVG found in ${url}`);
        }
        this.cache[url] = svg;
        return svg.cloneNode(true) as SVGElement;
      })
    );
  }
}
