CREATE TABLE foresporsel_om_deling_av_cv (
    id BIGSERIAL PRIMARY KEY,
    aktor_id TEXT,
    stilling_id UUID,
    delt_status TEXT,
    delt_tidspunkt TIMESTAMP(3),
    delt_av TEXT,
    svar TEXT,
    svar_tidspunkt TIMESTAMP(3)
)
