import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CommonModule } from '@angular/common';

interface PublicInfo {
    application: string;
    version: string;
    description: string;
}

@Component({
    selector: 'app-public',
    imports: [CommonModule],
    template: `
                <div class="container" data-testid="public-page">
                    <div class="page-header">
                        <h1>Public Information</h1>
                    </div>

                    <div *ngIf="loading()" class="loading">
                        <p>Loading...</p>
                    </div>

                    <div *ngIf="error()" class="error-box">
                        <p>{{ error() }}</p>
                    </div>

                    <div *ngIf="!loading() && !error() && info()" class="card">
                        <p><strong>Application:</strong> {{ info()!.application }}</p>
                        <p><strong>Version:</strong> {{ info()!.version }}</p>
                        <p><strong>Description:</strong> {{ info()!.description }}</p>
                    </div>
                </div>
    `,
})
export class PublicComponent implements OnInit {
    private http = inject(HttpClient);

    info = signal<PublicInfo | null>(null);
    loading = signal(true);
    error = signal<string | null>(null);

    ngOnInit(): void {
        this.fetchPublicInfo();
    }

    private fetchPublicInfo(): void {
        this.http.get<PublicInfo>('/public/info').subscribe({
            next: (data) => {
                this.info.set(data);
                this.loading.set(false);
            },
            error: (err) => {
                console.error('[PUBLIC] Failed to load public info:', err);
                this.error.set('Failed to load application information.');
                this.loading.set(false);
            }
        });
    }

}
