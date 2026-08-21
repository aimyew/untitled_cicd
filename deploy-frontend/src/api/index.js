import request from './request'

export const serverApi = {
  list: (params = {}) => request.get('/servers', { params }),
  create: (data) => request.post('/servers', data),
  update: (id, data) => request.put(`/servers/${id}`, data),
  remove: (id) => request.delete(`/servers/${id}`),
  test: (id) => request.post(`/servers/${id}/test`),
  listCommands: (id) => request.get(`/servers/${id}/commands`),
  execute: (id, commandId) => request.post(`/servers/${id}/execute/${commandId}`),
  generateSql: (id, data) => request.post(`/servers/${id}/generate-sql`, data)
}

export const projectApi = {
  list: (params = {}) => request.get('/projects', { params }),
  create: (data) => request.post('/projects', data),
  update: (id, data) => request.put(`/projects/${id}`, data),
  remove: (id) => request.delete(`/projects/${id}`)
}

export const deployApi = {
  start: (projectId) => request.post(`/deploy/${projectId}`),
  cancel: (recordId) => request.post(`/deploy/cancel/${recordId}`),
  records: (params) => request.get('/deploy/records', { params }),
  recordDetail: (id) => request.get(`/deploy/records/${id}`),
  lastByProjects: (projectIds) => request.get('/deploy/last', { params: { projectIds } })
}
