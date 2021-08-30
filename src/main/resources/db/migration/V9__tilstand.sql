ALTER TABLE foresporsel_om_deling_av_cv DROP COLUMN bruker_varslet;
ALTER TABLE foresporsel_om_deling_av_cv DROP COLUMN aktivitet_opprettet;
ALTER TABLE foresporsel_om_deling_av_cv DROP COLUMN svar;

ALTER TABLE foresporsel_om_deling_av_cv
    ADD COLUMN tilstand TEXT;

ALTER TABLE foresporsel_om_deling_av_cv
    ADD COLUMN svar BOOLEAN;

ALTER TABLE foresporsel_om_deling_av_cv
    ADD COLUMN svart_av_ident TEXT;

ALTER TABLE foresporsel_om_deling_av_cv
    ADD COLUMN svart_av_ident_type TEXT;
